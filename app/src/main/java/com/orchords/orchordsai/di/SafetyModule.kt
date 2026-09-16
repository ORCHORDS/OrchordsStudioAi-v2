package com.orchords.orchordsai.di

import com.orchords.orchordsai.data.safety.AiContentReportClient
import org.koin.dsl.module

val safetyModule = module {
    single { AiContentReportClient(httpClient = get()) }
}
