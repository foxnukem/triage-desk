# Eval run 10 — demo-full-pipeline-v3

Model `qwen3:8b`, rule router on, validation on, critic on. Cases: 8, errors: 0.

| Metric | Value |
|---|---|
| Category accuracy | 100% |
| Category macro-F1 | 100% |
| Priority exact | 25% |
| Priority within-1 | 100% |
| P1 recall | – |
| P1/P2 tickets kept ≥ P2 | 100% |
| Entity precision | 100% |
| Entity recall | 100% |
| Hallucinated entities (1st attempt) | 0% |
| Hallucinated entities (final) | 0% |
| All steps valid on 1st attempt | 75% |
| All steps valid after retries | 100% |
| Escalation precision | 100% |
| Escalation recall | 100% |
| Citation hit rate | 75% |
| Drafts violating policy (lint) | 13% |
| Sentiment (labelled cases) | 0% |
| Multi-intent detected | 100% |
| Latency p50, s | 169.0 |
| Latency p95, s | 287.8 |
| LLM calls per ticket | 4.9 |

Before rule overrides (router counterfactual): category accuracy 100%, P1 recall –, P1/P2 kept ≥ P2 100%.  
Drafts violating policy before the critic: 13%.

## Category confusion (rows = expected)

| | BILL | SHIP | RET | TECH | ACC | OTHER | 
|---|---|---|---|---|---|---|
| BILL | 1 | · | · | · | · | · | 
| SHIP | · | 5 | · | · | · | · | 
| RET | · | · | 1 | · | · | · | 
| TECH | · | · | · | · | · | · | 
| ACC | · | · | · | · | · | · | 
| OTHER | · | · | · | · | · | 1 | 

## Confidence calibration

| Confidence | Cases | Category accuracy |
|---|---|---|
| 0.0–0.6 | 0 | – |
| 0.6–0.8 | 0 | – |
| 0.8–0.9 | 0 | – |
| 0.9–0.95 | 0 | – |
| 0.95–1.0 | 8 | 100% |

## Cases

| Case | Slice | Expected | Actual | Human (exp/act) | Cited | Latency |
|---|---|---|---|---|---|---|
| t011 | standard | RETURN_REFUND P2 | RETURN_REFUND P2 | false / false | kb-defective-goods | 175.0 s |
| t031 | hard | BILLING P2 | BILLING P2 | false / false | kb-double-charge | 169.0 s |
| **t034** | hard | SHIPPING P3 | SHIPPING P2 | false / false | kb-lost-parcel | 109.1 s |
| **t041** | hard | OTHER P3 | OTHER P4 | false / false | kb-promo-codes, kb-refund-timing | 122.5 s |
| **t043** | hard | SHIPPING P3 | SHIPPING P2 | false / false | kb-order-tracking, kb-lost-parcel | 128.8 s |
| **t045** | adversarial | SHIPPING P3 | SHIPPING P2 | false / false | kb-shipping-times | 287.8 s |
| **t047** | adversarial | SHIPPING P3 | SHIPPING P2 | false / false | kb-order-tracking | 195.6 s |
| **t049** | adversarial | SHIPPING P3 | SHIPPING P2 | true / true | kb-shipping-times | 264.1 s |
