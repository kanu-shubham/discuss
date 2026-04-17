"""
Causal Inference in Insurance: Does a discount offer CAUSE customers to buy?

Flow:
  1. Generate realistic customer data
  2. Simulate biased discount assignment (marketing targets likely buyers)
  3. Naive ML estimate  → inflated/wrong causal effect
  4. Propensity Score Matching → true causal effect
"""

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

np.random.seed(42)
N = 5000


# ─────────────────────────────────────────────
# STEP 1: Generate Customer Data
# ─────────────────────────────────────────────
# Features that describe each customer
age            = np.random.randint(18, 70, N)
income         = np.random.normal(50000, 15000, N).clip(15000, 120000)
browsing_score = np.random.uniform(0, 1, N)   # how much they browsed insurance pages
risk_score     = np.random.uniform(0, 1, N)   # how risky/cautious they are

# True underlying intent to buy (NOT observable — hidden from us)
# High income, older, more browsing = more likely to buy naturally
true_intent = (
    0.3 * (age / 70) +
    0.3 * (income / 120000) +
    0.4 * browsing_score
)

data = pd.DataFrame({
    'age':            age,
    'income':         income,
    'browsing_score': browsing_score,
    'risk_score':     risk_score,
    'true_intent':    true_intent,
})


# ─────────────────────────────────────────────
# STEP 2: Biased Discount Assignment
# ─────────────────────────────────────────────
# Marketing is smart — they send discounts mostly to HIGH-INTENT customers.
# This creates selection bias: discount and purchase are correlated
# not because discount causes purchase, but because both are driven by intent.

discount_prob = (0.6 * true_intent + 0.1 * np.random.uniform(0, 1, N)).clip(0, 1)
data['received_discount'] = np.random.binomial(1, discount_prob)


# ─────────────────────────────────────────────
# STEP 3: Simulate Purchase Outcome
# ─────────────────────────────────────────────
# True causal effect of discount = +0.08 (8 percentage points only)
# But base purchase rate is driven mostly by true intent

TRUE_CAUSAL_EFFECT = 0.08

purchase_prob = (
    0.5 * true_intent +
    TRUE_CAUSAL_EFFECT * data['received_discount'] +
    0.05 * np.random.uniform(0, 1, N)
).clip(0, 1)

data['purchased'] = np.random.binomial(1, purchase_prob)


# ─────────────────────────────────────────────
# STEP 4: Naive Estimate (Wrong Way — Plain ML)
# ─────────────────────────────────────────────
# Just compare purchase rates: discount group vs no-discount group
# This is what a naive analyst or a plain ML model would do

rate_treated   = data[data['received_discount'] == 1]['purchased'].mean()
rate_untreated = data[data['received_discount'] == 0]['purchased'].mean()
naive_effect   = rate_treated - rate_untreated

print("=" * 55)
print("NAIVE ESTIMATE (Biased — ignores selection bias)")
print("=" * 55)
print(f"  Purchase rate with discount:     {rate_treated:.2%}")
print(f"  Purchase rate without discount:  {rate_untreated:.2%}")
print(f"  Naive causal effect:             +{naive_effect:.2%}")
print(f"  True causal effect:              +{TRUE_CAUSAL_EFFECT:.2%}")
print(f"  Overestimation:                  +{naive_effect - TRUE_CAUSAL_EFFECT:.2%}")
print()


# ─────────────────────────────────────────────
# STEP 5: Propensity Score Matching (Right Way)
# ─────────────────────────────────────────────
# Propensity score = probability of receiving discount given observed features
# We use it to find "twins": one got discount, one didn't, but both looked
# equally likely to get it. Any difference in outcome = caused by discount.

features = ['age', 'income', 'browsing_score', 'risk_score']
X = data[features]
T = data['received_discount']

scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

# Train logistic regression to predict who received the discount
ps_model = LogisticRegression()
ps_model.fit(X_scaled, T)

data['propensity_score'] = ps_model.predict_proba(X_scaled)[:, 1]

# Match each treated customer to the most similar untreated customer
treated   = data[data['received_discount'] == 1].copy()
untreated = data[data['received_discount'] == 0].copy()

matched_outcomes = []

for _, treated_row in treated.iterrows():
    # Find untreated customer with closest propensity score
    diffs = (untreated['propensity_score'] - treated_row['propensity_score']).abs()
    best_match_idx = diffs.idxmin()
    matched_control = untreated.loc[best_match_idx]
    matched_outcomes.append({
        'treated_purchased': treated_row['purchased'],
        'control_purchased': matched_control['purchased'],
    })

matched_df = pd.DataFrame(matched_outcomes)

psm_effect = (
    matched_df['treated_purchased'].mean() -
    matched_df['control_purchased'].mean()
)

print("=" * 55)
print("PROPENSITY SCORE MATCHING (Corrected Causal Estimate)")
print("=" * 55)
print(f"  Matched pairs:                   {len(matched_df)}")
print(f"  Purchase rate (treated):         {matched_df['treated_purchased'].mean():.2%}")
print(f"  Purchase rate (matched control): {matched_df['control_purchased'].mean():.2%}")
print(f"  Corrected causal effect:         +{psm_effect:.2%}")
print(f"  True causal effect:              +{TRUE_CAUSAL_EFFECT:.2%}")
print()


# ─────────────────────────────────────────────
# STEP 6: Business Interpretation
# ─────────────────────────────────────────────
discount_cost      = 50   # dollars per discount sent
revenue_per_policy = 800  # dollars per policy sold
customers_targeted = 10000

print("=" * 55)
print("BUSINESS IMPACT")
print("=" * 55)

for label, effect in [("Naive (wrong)", naive_effect), ("Causal (correct)", psm_effect)]:
    extra_buyers     = customers_targeted * effect
    extra_revenue    = extra_buyers * revenue_per_policy
    discount_spend   = customers_targeted * discount_cost
    net_profit       = extra_revenue - discount_spend
    print(f"\n  [{label}]")
    print(f"  Extra buyers from discount:  {extra_buyers:.0f}")
    print(f"  Extra revenue:               ${extra_revenue:,.0f}")
    print(f"  Discount spend:              ${discount_spend:,.0f}")
    print(f"  Net profit:                  ${net_profit:,.0f}")

print()
print("=" * 55)
print("CONCLUSION")
print("=" * 55)
print("""
  Naive ML overstates the discount's effect because it
  confuses correlation with causation.

  Propensity Score Matching removes selection bias by
  comparing customers who LOOKED equally likely to get
  a discount — isolating the discount's true causal impact.

  In practice: the discount causes only a fraction of
  the observed uplift. Without causal inference, the
  company wastes budget on customers who would have
  bought anyway.
""")
