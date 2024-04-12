package com.kaishustory.autoconfigure.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.monitor.MonitorService;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dingsheng
 */
@Activate(group = Constants.PROVIDER)
public class DubboMetricsFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DubboMetricsFilter.class);

    private static void collect(Invoker<?> invoker, Invocation invocation, String remoteHost, long start, boolean error) {
        try {
            long elapsed = System.currentTimeMillis() - start;
            String service = invoker.getInterface().getName();
            String method = RpcUtils.getMethodName(invocation);

            LOGGER.debug("{},{},{},{}", service, method, elapsed / 1000D, error);

            Tags tags = Tags.of("service", service,
                    "method", method,
                    "remoteHost", remoteHost,
                    "result", error ? MonitorService.FAILURE : MonitorService.SUCCESS
            );

            Metrics.summary("dubbo." + invoker.getUrl().getParameter(Constants.SIDE_KEY) + ".invoke.seconds", tags)
                    .record(elapsed / 1000D);
        } catch (Exception e) {
            LOGGER.error("Failed to monitor count service " + invoker.getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext context = RpcContext.getContext();

        String remoteHost = context.getRemoteHost();
        long start = System.currentTimeMillis();
        try {
            Result result = invoker.invoke(invocation);
            collect(invoker, invocation, remoteHost, start, result.hasException());
            return result;
        } catch (Exception e) {
            collect(invoker, invocation, remoteHost, start, true);
            throw e;
        }
    }
}
