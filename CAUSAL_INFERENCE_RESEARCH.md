# Causal Inference for Insurance Recommendation — Research Notes

## Problem Statement

Standard ML models predict **who will buy** insurance. But in a marketing context the
right question is: **who will buy *because* of our offer?**

Without causal inference, you waste offers on customers who would have purchased anyway,
and miss customers who only convert when given an incentive.

---

## DAG (Directed Acyclic Graph)

```
Age ──────────────────┐
Income ───────────────┼──→  Offer Sent  ──→  Purchase?
Family Size ──────────┤         ↑                ↑
Owns Car/Home ────────┘         │                │
Health Score ───────────────────┴────────────────┘
Job Stability ──────────────────────────────────┘
```

**Confounders** (affect both offer and purchase): age, income, browsing, health score.
These must be controlled for — otherwise any observed effect is biased.

---

## Method: T-Learner with Propensity Score Matching

### Why This Method

| Situation | Method Used |
|-----------|-------------|
| Cannot run A/B test on all customers | Propensity Score Matching |
| Want individual-level uplift scores | T-Learner (HTE estimation) |
| Want to rank products per customer | Causal Uplift Ranking |

### Propensity Score

The probability that a customer receives an offer, given their observable features:

```
P(T=1 | X) = LogisticRegression(age, income, family_size, owns_car,
                                  owns_home, health_score, job_stability, has_kids)
```

Used to find "twin" customers — one who got the offer, one who didn't, but both
looked equally likely to receive it. Any difference in outcome = causal effect.

### T-Learner (Heterogeneous Treatment Effects)

Two separate outcome models:

```
μ₁(x) = E[Y | X=x, T=1]   # outcome model for treated group
μ₀(x) = E[Y | X=x, T=0]   # outcome model for control group

τ(x)  = μ₁(x) - μ₀(x)     # individual causal uplift
```

Each customer gets a personalised uplift score per product.
Products are ranked by uplift to generate recommendations.

---

## Validation Checks

### 1. Balance Check (Standardized Mean Difference)
Measures whether treated and control groups are similar on observed features.

| SMD | Status |
|-----|--------|
| < 0.1 | Good |
| 0.1 – 0.2 | Acceptable |
| > 0.2 | Poor — re-examine |

### 2. Overlap Check
Propensity score distributions of treated and untreated groups must overlap.
No overlap = no valid comparison can be made.

### 3. ATE vs Naive Comparison
Causal ATE should be lower than naive difference (naive is inflated by selection bias).
ATE should be close to true effect (verified in simulation).

---

## Results Summary

| Product | Naive Effect | Causal ATE | True Effect |
|---------|-------------|------------|-------------|
| Health  | +10.5%      | +8.3%      | +9.0%       |
| Life    | +12.9%      | +6.1%      | +7.0%       |
| Auto    | +15.4%      | +8.3%      | +10.0%      |
| Home    | +15.1%      | +9.4%      | +8.0%       |

Naive estimates are consistently inflated by 2–7 percentage points due to selection bias.
Causal estimates are within 1–2 points of true effects.

---

## Segment Targeting Insights

| Product | Best Age Segment | Why |
|---------|-----------------|-----|
| Health  | 18–30           | Young customers respond most to health offers |
| Life    | 18–30           | Young families with kids show highest uplift |
| Auto    | 61–75           | Older customers least likely to buy without nudge |
| Home    | 18–30           | Renters converting to homeowners — high opportunity |

---

## Recommendation Logic

For each new customer:
1. Compute individual uplift `τ(x)` for each of the 4 products
2. Rank products by uplift (descending)
3. Recommend top N products

This ensures offers go to customers who will **change behaviour** because of the offer —
not to customers who were going to buy regardless.

---

## Files

| File | Description |
|------|-------------|
| `insurance_causal_inference.py` | Simple end-to-end demo with PSM |
| `insurance_recommender.py` | Full production pipeline |
| `CAUSAL_INFERENCE_RESEARCH.md` | This document |

---

## Further Reading

- Rubin (1974) — Potential Outcomes Framework
- Rosenbaum & Rubin (1983) — Propensity Score Matching
- Künzel et al. (2019) — Meta-learners for HTE (T/S/X-Learner)
- Wager & Athey (2018) — Causal Forests
