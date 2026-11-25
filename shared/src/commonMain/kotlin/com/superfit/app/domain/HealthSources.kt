package com.superfit.app.domain

/** One published source behind a number or tip the app shows. */
data class HealthSource(val title: String, val publisher: String, val url: String)

/**
 * What a health number or tip is based on: a plain explanation of how Superfit works it out, and the
 * sources it follows. Shown from the "Sources" link on each card and from Settings › Health info.
 */
enum class HealthTopic(val title: String, val explanation: String, val sources: List<HealthSource>) {
    CALORIES(
        title = "Calorie target",
        explanation = "Your resting calories come from the Mifflin-St Jeor equation (your weight, height, age and sex). " +
            "If a smart scale gives your body fat, Superfit uses the Katch-McArdle equation, which works from lean mass. " +
            "Your everyday activity is added on top (for example resting calories x 1.2 for a mostly seated day), workouts you " +
            "log or import can be added back, and your goal adjusts it: about 500 kcal less a day to lose weight, roughly " +
            "0.5 kg (1 lb) a week, which is the steady pace health agencies recommend. It never goes below 1,200 kcal.",
        sources = listOf(
            HealthSource(
                "A new predictive equation for resting energy expenditure in healthy individuals (Mifflin et al., 1990)",
                "American Journal of Clinical Nutrition, via PubMed (NIH)",
                "https://pubmed.ncbi.nlm.nih.gov/2305711/"
            ),
            HealthSource(
                "Comparison of predictive equations for resting metabolic rate: a systematic review (Frankenfield et al., 2005)",
                "Journal of the American Dietetic Association, via PubMed (NIH)",
                "https://pubmed.ncbi.nlm.nih.gov/15883556/"
            ),
            HealthSource("Steps for Losing Weight", "U.S. Centers for Disease Control and Prevention (CDC)", "https://www.cdc.gov/healthy-weight-growth/losing-weight/index.html")
        )
    ),
    PROTEIN(
        title = "Protein, carbs and fat",
        explanation = "Protein is set at about 1.6 g per kg of body weight (2.0 g if you're building muscle), the amount research " +
            "links to keeping and building muscle. Fat is about 25% of your calories and carbs fill the rest. " +
            "If your nutritionist gave you numbers, Superfit uses theirs instead.",
        sources = listOf(
            HealthSource(
                "Protein supplementation and resistance training gains in healthy adults: a systematic review and meta-analysis (Morton et al., 2018)",
                "British Journal of Sports Medicine, via PubMed (NIH)",
                "https://pubmed.ncbi.nlm.nih.gov/28698222/"
            ),
            HealthSource(
                "Position stand: protein and exercise (Jäger et al., 2017)",
                "International Society of Sports Nutrition, via PubMed (NIH)",
                "https://pubmed.ncbi.nlm.nih.gov/28642676/"
            ),
            HealthSource("Healthy diet fact sheet", "World Health Organization (WHO)", "https://www.who.int/news-room/fact-sheets/detail/healthy-diet")
        )
    ),
    FIBRE(
        title = "Fibre",
        explanation = "Your fibre target is about 14 g for every 1,000 kcal you eat (between 25 and 38 g a day), the amount " +
            "dietary guidelines recommend for adults.",
        sources = listOf(
            HealthSource("Dietary Guidelines for Americans", "U.S. Departments of Agriculture and Health and Human Services", "https://www.dietaryguidelines.gov/"),
            HealthSource(
                "Health implications of dietary fiber (position paper)",
                "Academy of Nutrition and Dietetics, via PubMed (NIH)",
                "https://pubmed.ncbi.nlm.nih.gov/26514720/"
            )
        )
    ),
    DIET_QUALITY(
        title = "Diet quality and food ratings",
        explanation = "Meal ratings and your diet quality score follow mainstream healthy-eating advice: enough protein, " +
            "plenty of vegetables, fruit, whole grains and pulses, mostly home-cooked or whole foods, and treats, sugar " +
            "and ultra-processed food kept occasional.",
        sources = listOf(
            HealthSource("Healthy diet fact sheet", "World Health Organization (WHO)", "https://www.who.int/news-room/fact-sheets/detail/healthy-diet"),
            HealthSource(
                "Dietary Guidelines for Indians (2024)",
                "Indian Council of Medical Research, National Institute of Nutrition (ICMR-NIN)",
                "https://www.nin.res.in/dietaryguidelines/pdfjs/locale/DGI24thJune2024fin.pdf"
            ),
            HealthSource("Dietary Guidelines for Americans", "U.S. Departments of Agriculture and Health and Human Services", "https://www.dietaryguidelines.gov/")
        )
    ),
    STEPS(
        title = "Steps and activity",
        explanation = "Your step goal starts from how much you already walk and rises slowly towards a healthy level for your " +
            "age: 8,000 to 10,000 steps a day under 60 and 6,000 to 8,000 from 60, where studies find most of the health " +
            "benefit of walking. Tips about exercise follow the advice of at least 150 minutes of moderate activity and " +
            "2 days of muscle strengthening a week.",
        sources = listOf(
            HealthSource(
                "Daily steps and all-cause mortality: a meta-analysis of 15 international cohorts (Paluch et al., 2022)",
                "The Lancet Public Health, via PubMed (NIH)",
                "https://pubmed.ncbi.nlm.nih.gov/35247352/"
            ),
            HealthSource("Guidelines on physical activity and sedentary behaviour", "World Health Organization (WHO)", "https://www.who.int/publications/i/item/9789240015128"),
            HealthSource("Adult Activity: An Overview", "U.S. Centers for Disease Control and Prevention (CDC)", "https://www.cdc.gov/physical-activity-basics/guidelines/adults.html")
        )
    ),
    SLEEP(
        title = "Sleep",
        explanation = "Adults need at least 7 hours of sleep a night. Superfit compares last night's sleep with 8 hours and " +
            "looks at how much of it was deep sleep, to suggest a harder or an easier day.",
        sources = listOf(
            HealthSource("Sleep in adults: facts and stats", "U.S. Centers for Disease Control and Prevention (CDC)", "https://www.cdc.gov/sleep/data-research/facts-stats/adults-sleep-facts-and-stats.html")
        )
    ),
    COACH(
        title = "AI coach tips",
        explanation = "The AI coach's tips are worked out by the app from your own logs, measured against the targets above " +
            "and their sources. The AI only puts them into words. Tips are general wellness guidance for healthy adults.",
        sources = emptyList()
    );

    companion object {
        /** Shown under every list of sources and in Settings. */
        const val DISCLAIMER = "Superfit gives general wellness information, not medical advice. It doesn't diagnose or " +
            "treat any condition. Talk to your doctor before changing your diet or exercise, especially if you are " +
            "pregnant, under 18, or have a medical condition or a history of eating disorders."
    }
}
