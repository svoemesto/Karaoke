//package com.svoemesto.karaokeapp.controllers
//
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.context.ApplicationContext
//import org.springframework.context.annotation.AnnotationConfigApplicationContext
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.core.task.AsyncTaskExecutor
//import org.springframework.core.task.SimpleAsyncTaskExecutor
//import java.util.concurrent.Callable
//import java.util.concurrent.Future
//
//
//class AsyncTaskExecutorExample {
//    fun main(args: Array<String>) {
//        val context: ApplicationContext = AnnotationConfigApplicationContext(MyConfig::class.java)
//        val bean: MyBean = context.getBean(MyBean::class.java)
//        bean.runTasks()
//    }
//
//    @Configuration
//    class MyConfig {
//        @Bean
//        fun myBean(): MyBean {
//            return MyBean()
//        }
//
//        @Bean
//        fun taskExecutor(): AsyncTaskExecutor {
//            val t = SimpleAsyncTaskExecutor()
//            t.concurrencyLimit = 100
//            return t
//        }
//    }
//
//    class MyBean {
//        @Autowired
//        private val executor: AsyncTaskExecutor? = null
//        @Throws(Exception::class)
//        fun runTasks() {
//            val futureList: MutableList<Future<*>> = ArrayList()
//            for (i in 0..9) {
//                val future: Future<*> = executor!!.submit(getTask(i))
//                futureList.add(future)
//            }
//            for (future in futureList) {
//                println(future.get())
//            }
//        }
//
//        private fun getTask(i: Int): Callable<String> {
//            return Callable<String> {
//                System.out.printf(
//                    "running task %d. Thread: %s%n",
//                    i,
//                    Thread.currentThread().name
//                )
//                String.format("Task finished %d", i)
//            }
//        }
//    }
//}