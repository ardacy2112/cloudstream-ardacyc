package com.ardacy2112

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SelcukflixPlugin : Plugin() {
    override fun load() {
        registerMainAPI(SelcukflixProvider())
    }
}
