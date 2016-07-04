package akka.dispatch

import com.typesafe.config.Config
import akka.util.Helpers.ConfigOps
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ExecutorService
import akkasched.WaitableExecutorServiceWrapper

class TracingDispatcherConfigurator(config2: Config, prerequisites2: DispatcherPrerequisites) extends MessageDispatcherConfigurator(config2, prerequisites2) {
  
  class WaitableExecutorFactory(val wrapped: ExecutorServiceFactory) extends ExecutorServiceFactory {
    final def createExecutorService: ExecutorService = {
      new WaitableExecutorServiceWrapper(wrapped.createExecutorService)
    }
  }
  
  class WaitableExecutorFactoryProvider(val wrapped: ExecutorServiceFactoryProvider) extends ExecutorServiceFactoryProvider {
    final def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {
      new WaitableExecutorFactory(wrapped.createExecutorServiceFactory(id, threadFactory))
    }
  }

  private val instance = new TracingDispatcher(
      this,
      config.getString("id"),
      config.getInt("throughput"),
      config.getNanosDuration("throughput-deadline-time"),
      new WaitableExecutorFactoryProvider(configureExecutor()),
      config.getMillisDuration("shutdown-timeout"))

  /**
   * Returns the same dispatcher instance for each invocation
   */
  override def dispatcher(): MessageDispatcher = instance
}