package com.superfit.app.domain

/** A finding shown on Insights or Journey: a bold title, one line of detail, and a short phrase for the week's verdict. */
data class Insight(val id: String, val title: String, val detail: String, val phrase: String)
