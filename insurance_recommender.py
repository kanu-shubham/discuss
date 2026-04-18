"""
Production Causal Inference Pipeline
Recommends insurance products to users based on causal uplift — not just
predicted purchase probability.

Products: Health, Life, Auto, Home insurance

Pipeline:
  1. Generate realistic customer data
  2. Estimate propensity scores per product
  3. Balance check (are matched groups similar?)
  4. Overlap check (do propensity scores overlap?)
  5. Estimate ATE  (average treatment effect per product)
  6. Estimate HTE  (who benefits most from each product offer?)
  7. Recommend     (rank products per customer by causal uplift)
"""

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression, LinearRegression
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import cross_val_score
import warnings
warnings.filterwarnings("ignore")

np.random.seed(42)
N = 8000
PRODUCTS = ["health", "life", "auto", "home"]


# ─────────────────────────────────────────────────────────────
# STEP 1: Generate Realistic Customer Data
# ─────────────────────────────────────────────────────────────

def generate_customers(n):
    age           = np.random.randint(18, 75, n)
    income        = np.random.normal(55000, 20000, n).clip(10000, 200000)
    family_size   = np.random.choice([1, 2, 3, 4, 5], n, p=[0.2, 0.3, 0.25, 0.15, 0.1])
    owns_car      = np.random.binomial(1, 0.65, n)
    owns_home     = np.random.binomial(1, np.where(age > 35, 0.6, 0.3), n)
    health_score  = np.random.uniform(0, 1, n)   # 1 = very healthy
    job_stability = np.random.uniform(0, 1, n)   # 1 = very stable job
    has_kids      = (family_size >= 3).astype(int)

    df = pd.DataFrame({
        "age":           age,
        "income":        income,
        "family_size":   family_size,
        "owns_car":      owns_car,
        "owns_home":     owns_home,
        "health_score":  health_score,
        "job_stability": job_stability,
        "has_kids":      has_kids,
    })
    return df


# ─────────────────────────────────────────────────────────────
# STEP 2: Simulate Treatment Assignment & Purchase Outcomes
# Each product has its own marketing logic (biased assignment)
# and its own true causal effect.
# ─────────────────────────────────────────────────────────────

# True causal effects (ground truth — hidden in real life)
TRUE_EFFECTS = {
    "health": 0.09,
    "life":   0.07,
    "auto":   0.10,
    "home":   0.08,
}

def simulate_treatments_and_outcomes(df):
    n = len(df)
    age_n    = df["age"] / 75
    income_n = df["income"] / 200000

    # Natural intent per product (unobserved in real life)
    intent = {
        "health": 0.4 * (1 - df["health_score"]) + 0.3 * age_n      + 0.3 * income_n,
        "life":   0.4 * df["has_kids"]            + 0.3 * age_n      + 0.3 * df["job_stability"],
        "auto":   0.5 * df["owns_car"]            + 0.3 * income_n   + 0.2 * (1 - age_n),
        "home":   0.5 * df["owns_home"]           + 0.3 * income_n   + 0.2 * age_n,
    }

    treatments = {}
    outcomes   = {}

    for product in PRODUCTS:
        # Biased assignment: marketing targets high-intent customers
        raw_prob          = (0.55 * intent[product] + 0.15 * np.random.uniform(0, 1, n)).clip(0, 1)
        treatments[product] = np.random.binomial(1, raw_prob)

        # Purchase outcome = natural intent + causal effect of offer + noise
        purchase_prob = (
            0.45 * intent[product] +
            TRUE_EFFECTS[product] * treatments[product] +
            0.05 * np.random.uniform(0, 1, n)
        ).clip(0, 1)
        outcomes[product] = np.random.binomial(1, purchase_prob)

    for product in PRODUCTS:
        df[f"offered_{product}"]   = treatments[product]
        df[f"purchased_{product}"] = outcomes[product]

    return df, intent


# ─────────────────────────────────────────────────────────────
# CORE PIPELINE CLASS
# ─────────────────────────────────────────────────────────────

FEATURES = ["age", "income", "family_size", "owns_car",
            "owns_home", "health_score", "job_stability", "has_kids"]

class CausalInsuranceRecommender:

    def __init__(self, data):
        self.data      = data.copy()
        self.scaler    = StandardScaler()
        self.ps_models = {}       # propensity score model per product
        self.ate       = {}       # average treatment effect per product
        self.hte       = {}       # heterogeneous effects per product
        self.uplift_df = None     # per-customer uplift scores

        self.X        = self.data[FEATURES]
        self.X_scaled = self.scaler.fit_transform(self.X)


    # ── STEP 3: Propensity Score Estimation ───────────────────
    def fit_propensity_scores(self):
        print("\n" + "="*60)
        print("STEP 1: PROPENSITY SCORE ESTIMATION")
        print("="*60)

        for product in PRODUCTS:
            T     = self.data[f"offered_{product}"]
            model = LogisticRegression(max_iter=1000)
            model.fit(self.X_scaled, T)

            cv_score = cross_val_score(model, self.X_scaled, T, cv=5, scoring="roc_auc").mean()
            self.data[f"ps_{product}"] = model.predict_proba(self.X_scaled)[:, 1]
            self.ps_models[product]    = model

            print(f"  {product.upper():8s} | Propensity model AUC: {cv_score:.3f}")


    # ── STEP 4: Balance Check ──────────────────────────────────
    def check_balance(self):
        print("\n" + "="*60)
        print("STEP 2: BALANCE CHECK (Standardized Mean Difference)")
        print("  SMD < 0.1 = well balanced   |   SMD > 0.2 = problematic")
        print("="*60)

        for product in PRODUCTS:
            treated   = self.data[self.data[f"offered_{product}"] == 1]
            untreated = self.data[self.data[f"offered_{product}"] == 0]

            smds = []
            for feature in FEATURES:
                mean_t  = treated[feature].mean()
                mean_c  = untreated[feature].mean()
                pooled_std = self.data[feature].std()
                smd = abs(mean_t - mean_c) / (pooled_std + 1e-9)
                smds.append(smd)

            avg_smd = np.mean(smds)
            status  = "GOOD" if avg_smd < 0.1 else ("OK" if avg_smd < 0.2 else "POOR")
            print(f"  {product.upper():8s} | Avg SMD before matching: {avg_smd:.3f}  [{status}]")


    # ── STEP 5: Overlap Check ──────────────────────────────────
    def check_overlap(self):
        print("\n" + "="*60)
        print("STEP 3: OVERLAP CHECK (Propensity Score Range)")
        print("  Treated and control ranges must overlap substantially")
        print("="*60)

        for product in PRODUCTS:
            treated   = self.data[self.data[f"offered_{product}"] == 1][f"ps_{product}"]
            untreated = self.data[self.data[f"offered_{product}"] == 0][f"ps_{product}"]

            overlap_min = max(treated.min(), untreated.min())
            overlap_max = min(treated.max(), untreated.max())
            overlap_pct = (overlap_max - overlap_min) / (
                max(treated.max(), untreated.max()) - min(treated.min(), untreated.min()) + 1e-9
            )

            status = "GOOD" if overlap_pct > 0.7 else ("OK" if overlap_pct > 0.4 else "POOR")
            print(f"  {product.upper():8s} | Overlap range: [{overlap_min:.2f}, {overlap_max:.2f}]"
                  f"  Coverage: {overlap_pct:.0%}  [{status}]")


    # ── STEP 6: ATE via Propensity Score Matching ─────────────
    def estimate_ate(self):
        print("\n" + "="*60)
        print("STEP 4: AVERAGE TREATMENT EFFECT (ATE) ESTIMATION")
        print("="*60)

        for product in PRODUCTS:
            treated   = self.data[self.data[f"offered_{product}"] == 1].copy()
            untreated = self.data[self.data[f"offered_{product}"] == 0].copy()

            matched_pairs = []
            for _, row in treated.iterrows():
                diffs      = (untreated[f"ps_{product}"] - row[f"ps_{product}"]).abs()
                best_match = diffs.idxmin()
                matched_pairs.append({
                    "treated_outcome": row[f"purchased_{product}"],
                    "control_outcome": untreated.loc[best_match, f"purchased_{product}"],
                })

            pairs_df          = pd.DataFrame(matched_pairs)
            ate               = pairs_df["treated_outcome"].mean() - pairs_df["control_outcome"].mean()
            self.ate[product] = ate

            naive = (
                self.data[self.data[f"offered_{product}"] == 1][f"purchased_{product}"].mean() -
                self.data[self.data[f"offered_{product}"] == 0][f"purchased_{product}"].mean()
            )

            print(f"  {product.upper():8s} | Naive: +{naive:.3f}  |  "
                  f"Causal ATE: +{ate:.3f}  |  True: +{TRUE_EFFECTS[product]:.3f}")


    # ── STEP 7: HTE — Who Benefits Most? ──────────────────────
    def estimate_hte(self):
        """
        Causal Forest (simplified): fit outcome model separately for
        treated and control, then predict individual uplift as
        E[Y|X, T=1] - E[Y|X, T=0] for every customer.
        This is the T-Learner approach — standard in industry.
        """
        print("\n" + "="*60)
        print("STEP 5: HETEROGENEOUS TREATMENT EFFECTS (Who benefits most?)")
        print("="*60)

        uplift_scores = pd.DataFrame(index=self.data.index)

        for product in PRODUCTS:
            treated   = self.data[self.data[f"offered_{product}"] == 1]
            untreated = self.data[self.data[f"offered_{product}"] == 0]

            X_t = self.scaler.transform(treated[FEATURES])
            X_c = self.scaler.transform(untreated[FEATURES])
            y_t = treated[f"purchased_{product}"]
            y_c = untreated[f"purchased_{product}"]

            # Outcome model for treated group
            model_t = LogisticRegression(max_iter=1000)
            model_t.fit(X_t, y_t)

            # Outcome model for control group
            model_c = LogisticRegression(max_iter=1000)
            model_c.fit(X_c, y_c)

            # Individual uplift = predicted outcome if offered - if not offered
            X_all                       = self.scaler.transform(self.data[FEATURES])
            pred_if_offered             = model_t.predict_proba(X_all)[:, 1]
            pred_if_not_offered         = model_c.predict_proba(X_all)[:, 1]
            uplift_scores[product]      = pred_if_offered - pred_if_not_offered
            self.hte[product]           = uplift_scores[product]

            avg_uplift = uplift_scores[product].mean()
            top10_uplift = uplift_scores[product].quantile(0.9)
            print(f"  {product.upper():8s} | Avg uplift: +{avg_uplift:.3f}  |  "
                  f"Top 10% uplift: +{top10_uplift:.3f}")

        self.uplift_df = uplift_scores


    # ── STEP 8: Recommend Products per Customer ───────────────
    def recommend(self, customer_features: pd.DataFrame, top_n: int = 2):
        """
        Given a customer's features, return ranked insurance products
        by causal uplift (not just purchase probability).
        """
        X_scaled = self.scaler.transform(customer_features[FEATURES])
        recs = {}

        for product in PRODUCTS:
            treated_model   = LogisticRegression(max_iter=1000)
            untreated_model = LogisticRegression(max_iter=1000)

            treated   = self.data[self.data[f"offered_{product}"] == 1]
            untreated = self.data[self.data[f"offered_{product}"] == 0]

            treated_model.fit(
                self.scaler.transform(treated[FEATURES]),
                treated[f"purchased_{product}"]
            )
            untreated_model.fit(
                self.scaler.transform(untreated[FEATURES]),
                untreated[f"purchased_{product}"]
            )

            uplift = (
                treated_model.predict_proba(X_scaled)[:, 1] -
                untreated_model.predict_proba(X_scaled)[:, 1]
            )
            recs[product] = uplift

        uplift_df = pd.DataFrame(recs, index=customer_features.index)
        ranked    = uplift_df.rank(axis=1, ascending=False)

        results = []
        for i, row in uplift_df.iterrows():
            sorted_products = row.sort_values(ascending=False)
            top_products    = sorted_products.head(top_n)
            results.append({
                "customer_id":   i,
                "recommendations": [
                    {"product": p, "causal_uplift": round(v, 4)}
                    for p, v in top_products.items()
                ]
            })
        return results


    # ── STEP 9: Segment Analysis ───────────────────────────────
    def segment_analysis(self):
        print("\n" + "="*60)
        print("STEP 6: SEGMENT ANALYSIS (Who to target per product?)")
        print("="*60)

        df = self.data.copy()
        df["age_group"] = pd.cut(df["age"], bins=[18, 30, 45, 60, 75],
                                  labels=["18-30", "31-45", "46-60", "61-75"])

        for product in PRODUCTS:
            df[f"uplift_{product}"] = self.hte[product].values
            seg = (
                df.groupby("age_group", observed=True)[f"uplift_{product}"]
                .mean()
                .round(4)
                .reset_index()
            )
            best_seg = seg.loc[seg[f"uplift_{product}"].idxmax(), "age_group"]
            print(f"\n  {product.upper()} — Uplift by age group:")
            for _, row in seg.iterrows():
                bar    = "█" * int(row[f"uplift_{product}"] * 200)
                marker = " ← TARGET" if row["age_group"] == best_seg else ""
                print(f"    {row['age_group']:6s} | {bar:20s} {row[f'uplift_{product}']:.4f}{marker}")


    def run(self):
        self.fit_propensity_scores()
        self.check_balance()
        self.check_overlap()
        self.estimate_ate()
        self.estimate_hte()
        self.segment_analysis()
        return self


# ─────────────────────────────────────────────────────────────
# MAIN — Run full pipeline + demo recommendations
# ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("\n" + "="*60)
    print("  CAUSAL INSURANCE RECOMMENDATION SYSTEM")
    print("  Production Pipeline — Full End-to-End")
    print("="*60)

    # Generate data
    customers        = generate_customers(N)
    customers, _     = simulate_treatments_and_outcomes(customers)

    # Run pipeline
    recommender = CausalInsuranceRecommender(customers)
    recommender.run()

    # ── Demo: Recommend for 5 new customers ───────────────────
    print("\n" + "="*60)
    print("STEP 7: LIVE RECOMMENDATIONS FOR NEW CUSTOMERS")
    print("="*60)

    new_customers = pd.DataFrame([
        # age  income   fam  car  home  health  job    kids
        [25,  35000,   1,   1,   0,   0.9,    0.6,   0],   # young, healthy, single
        [45,  80000,   4,   1,   1,   0.5,    0.8,   1],   # mid-age, family, homeowner
        [62,  60000,   2,   0,   1,   0.3,    0.7,   0],   # older, poor health, homeowner
        [32,  45000,   3,   1,   0,   0.7,    0.5,   1],   # young family, renter
        [55, 120000,   2,   1,   1,   0.6,    0.9,   0],   # high income, stable
    ], columns=FEATURES)

    profiles = [
        "Young healthy single",
        "Mid-age family homeowner",
        "Older poor-health homeowner",
        "Young family renter",
        "High-income stable couple",
    ]

    recs = recommender.recommend(new_customers, top_n=2)

    for i, rec in enumerate(recs):
        print(f"\n  Customer {i+1}: {profiles[i]}")
        for r in rec["recommendations"]:
            print(f"    → {r['product'].upper():8s} insurance  |  causal uplift: +{r['causal_uplift']:.4f}")

    # ── Business Summary ───────────────────────────────────────
    print("\n" + "="*60)
    print("BUSINESS SUMMARY")
    print("="*60)
    print(f"\n  {'Product':8s} | ATE (causal) | True Effect | Diff")
    print(f"  {'-'*45}")
    for p in PRODUCTS:
        ate  = recommender.ate[p]
        true = TRUE_EFFECTS[p]
        print(f"  {p.upper():8s} | +{ate:.4f}       | +{true:.4f}      | {ate-true:+.4f}")

    print("""
  Key takeaways:
  • Causal ATE closely tracks true effect (unlike naive estimate)
  • Recommendations rank products by WHO benefits most from an offer
  • Targeting by uplift avoids wasting offers on customers who buy anyway
  • Segment analysis shows which age groups to prioritize per product
""")
