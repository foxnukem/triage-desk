package triage.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import triage.ai.Category;
import triage.ai.Priority;

class RuleRouterTest {

    private final RuleRouter router = new RuleRouter();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            My power bank started smoking while charging                    | safety-hazard
            Павербанк задимівся під час зарядки                             | safety-hazard
            Батарея телефону роздулась                                       | safety-hazard
            Someone logged into my account and changed the address          | account-compromise
            Хтось зайшов у мій акаунт і оформив замовлення                   | account-compromise
            You charged me twice for order GK-104233                        | double-charge
            З картки списали гроші двічі                                     | double-charge
            Оплата не проходить, пише помилку                                | payment-outage
            I can't pay with my card at checkout                            | payment-outage
            """)
    void matchesRule(String text, String ruleId) {
        assertThat(router.match(text)).get().extracting(RuleRouter.RuleHit::ruleId).isEqualTo(ruleId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Where is my parcel? It has been a week.",
            "Хочу повернути навушники, не підійшов розмір",
            "How do I reset my password?",
            "Can I pay by card on delivery?",
    })
    void noRuleForOrdinaryTickets(String text) {
        assertThat(router.match(text)).isEmpty();
    }

    @Test
    void keywordRulesAreBluntOnPurpose() {
        // A false P1 costs an agent a glance; a missed fire costs much more.
        assertThat(router.match("I quit smoking last year, anyway my headphones are broken")).isPresent();
    }

    @Test
    void safetyRuleHasNoCategoryAndAccountRuleForcesP1() {
        var safety = router.match("the charger is sparking").orElseThrow();
        assertThat(safety.category()).isNull();
        assertThat(safety.minPriority()).isEqualTo(Priority.P1);
        var account = router.match("my account was hacked").orElseThrow();
        assertThat(account.category()).isEqualTo(Category.ACCOUNT);
        assertThat(account.minPriority()).isEqualTo(Priority.P1);
    }

    @Test
    void queues() {
        assertThat(router.queueFor(Category.SHIPPING, Priority.P1)).isEqualTo("urgent");
        assertThat(router.queueFor(Category.SHIPPING, Priority.P3)).isEqualTo("logistics");
        assertThat(router.queueFor(Category.BILLING, Priority.P2)).isEqualTo("billing");
        assertThat(router.queueFor(null, Priority.P4)).isEqualTo("general");
    }
}
