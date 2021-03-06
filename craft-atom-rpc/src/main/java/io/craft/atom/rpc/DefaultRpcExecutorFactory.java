package io.craft.atom.rpc;

import io.craft.atom.rpc.api.RpcParameter;
import io.craft.atom.rpc.spi.RpcApi;
import io.craft.atom.rpc.spi.RpcExecutorFactory;
import io.craft.atom.rpc.spi.RpcRegistry;
import io.craft.atom.util.thread.MonitoringExecutorService;
import io.craft.atom.util.thread.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;


/**
 * @author mindwind
 * @version 1.0, Aug 12, 2014
 */
public class DefaultRpcExecutorFactory implements RpcExecutorFactory {

	
	@Getter @Setter private RpcRegistry                            registry;
	@Getter @Setter private Map<String, MonitoringExecutorService> pool    ;
	
	
	// ~ ------------------------------------------------------------------------------------------------------------
	
	
	public DefaultRpcExecutorFactory() {
		this.pool = new ConcurrentHashMap<String, MonitoringExecutorService>();
	}
	
	
	// ~ ------------------------------------------------------------------------------------------------------------
	
	
	@Override
	public MonitoringExecutorService getExecutor(RpcApi api) {
		return getExecutor0(api);
	}
	
	private MonitoringExecutorService getExecutor0(RpcApi queryApi) {
		String key = queryApi.getKey();
		MonitoringExecutorService es = pool.get(key);
		if (es == null) {
			synchronized (this) {
				if (es == null) {
					RpcApi api = registry.lookup(queryApi);
					if (api == null) { throw new RpcException(RpcException.SERVER_ERROR, "No exported api mapping"); } 
					
					RpcParameter parameter = api.getRpcParameter();
					int          threads   = parameter.getRpcThreads() == 0 ? 1 : parameter.getRpcThreads();
					int          queues    = parameter.getRpcQueues()  == 0 ? 1 : parameter.getRpcQueues() ;
					RpcThreadPoolExecutor tpe = new RpcThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(queues), new NamedThreadFactory("craft-atom-rpc"));
					tpe.allowCoreThreadTimeOut(true);
					es = tpe;
					pool.put(key, tpe);
				}
			}
		}
		return es;
	}


	@Override
	public void shutdown() {
		for (MonitoringExecutorService mes : pool.values()) {
			mes.shutdownNow();
		}
	}

}
