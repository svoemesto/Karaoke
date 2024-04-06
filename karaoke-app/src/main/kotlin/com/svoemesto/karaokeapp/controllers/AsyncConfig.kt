//package com.svoemesto.karaokeapp.controllers
//
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.context.annotation.Primary
//import org.springframework.core.task.AsyncTaskExecutor
//import org.springframework.core.task.SimpleAsyncTaskExecutor
//import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor
//
//@Configuration
//class AsyncConfig {
//    @Primary
//    @Bean
//    @Qualifier("notificationsExecutor")
//    fun taskExecutor(): AsyncTaskExecutor {
//        val t = SimpleAsyncTaskExecutor()
//        t.concurrencyLimit = 100
//        return t
//    }
//}