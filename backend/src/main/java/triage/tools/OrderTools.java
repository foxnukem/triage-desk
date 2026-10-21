package triage.tools;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import triage.persistence.OrderEntity;
import triage.persistence.OrderRepository;

@Component
public class OrderTools {

    private final OrderRepository orders;
    private final ObjectMapper mapper;

    public OrderTools(OrderRepository orders, ObjectMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    @Tool("Look up a Gadgetko order by its number (GK-123456): status, date, items, total.")
    public String getOrder(@P("order number, e.g. GK-104233") String orderId) {
        String id = normalize(orderId);
        Optional<OrderEntity> order = orders.findById(id);
        if (order.isEmpty()) {
            return "Order " + id + " not found.";
        }
        OrderEntity o = order.get();
        StringBuilder items = new StringBuilder();
        try {
            for (var item : mapper.readValue(o.getItems(), new TypeReference<java.util.List<java.util.Map<String, Object>>>() {})) {
                if (!items.isEmpty()) {
                    items.append("; ");
                }
                items.append(item.get("name")).append(" x").append(item.get("qty")).append(" (").append(item.get("price")).append(" UAH)");
            }
        } catch (Exception e) {
            items.append(o.getItems());
        }
        return "Order %s: status %s, placed %s, items: %s, total %s UAH".formatted(
                o.getOrderId(), o.getStatus(), o.getCreatedAt().toLocalDate(), items, o.getAmount().toPlainString());
    }

    public static String normalize(String orderId) {
        String digits = orderId == null ? "" : orderId.replaceAll("\\D", "");
        return digits.length() == 6 ? "GK-" + digits : orderId == null ? "" : orderId.strip().toUpperCase(Locale.ROOT);
    }
}
