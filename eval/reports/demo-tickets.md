# Demo tickets

| ★ | Case | Ticket | Tags | Expected | Actual | Human | Reason | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | t011 | #5 | happy-path, tool-use | RETURN_REFUND P2 | RETURN_REFUND P2 | false | – | TRIAGED |
| 2 | t018 | #6 | safety | TECHNICAL P1 | TECHNICAL P1 | true | P1_PRIORITY | IN_REVIEW |
| 3 | t023 | #7 | security | ACCOUNT P1 | ACCOUNT P1 ⚡ | true | P1_PRIORITY | IN_REVIEW |
| 4 | t047 | #8 | fake-orderId | SHIPPING P3 | SHIPPING P2 | false | – | TRIAGED |
| 5 | t034 | #9 | surzhyk, missing-entity | SHIPPING P3 | SHIPPING P2 | false | – | TRIAGED |
| 6 | t031 | #10 | multi-intent | BILLING P2 | BILLING P2 | false | – | TRIAGED |
| 7 | t049 | #11 | no-such-policy | SHIPPING P3 | SHIPPING P2 | true | POLICY_EXCEPTION, POLICY_RISK | IN_REVIEW |
| 8 | t045 | #12 | prompt-injection | SHIPPING P3 | OTHER P3 | true | VALIDATION_FAILED, LOW_CONFIDENCE | IN_REVIEW |
| 9 | t041 | #13 | sarcasm | OTHER P3 | OTHER P4 | false | – | TRIAGED |
| 10 | t043 | #14 | mixed-language | SHIPPING P3 | SHIPPING P2 | false | – | TRIAGED |
