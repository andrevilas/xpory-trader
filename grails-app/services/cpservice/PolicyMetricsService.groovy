package cpservice

import grails.gorm.transactions.Transactional
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

import java.util.concurrent.TimeUnit

@Transactional
class PolicyMetricsService {

    MeterRegistry meterRegistry

    Timer policyFetchTimer
    Timer policyPullTimer

    PolicyMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry
        this.policyFetchTimer = io.micrometer.core.instrument.Timer.builder('cp.policies.fetch.latency')
                .publishPercentiles(0.95)
                .description('Latency to serve baseline policy reads (p95 < 150ms)')
                .register(meterRegistry)
        this.policyPullTimer = io.micrometer.core.instrument.Timer.builder('cp.policies.pull.latency')
                .publishPercentiles(0.95)
                .description('Latency to aggregate policy pull payloads for agents')
                .register(meterRegistry)
    }

    def <T> T timePolicyFetch(Closure<T> action) {
        long start = System.nanoTime()
        try {
            T result = action.call()
            recordSuccess(System.nanoTime() - start, result != null)
            return result
        } catch (Throwable throwable) {
            recordFailure(System.nanoTime() - start)
            throw throwable
        }
    }

    private void recordSuccess(long nanos, boolean hit) {
        policyFetchTimer.record(nanos, TimeUnit.NANOSECONDS)
        meterRegistry.counter('cp.policies.fetch.result', 'outcome', hit ? 'hit' : 'miss').increment()
    }

    private void recordFailure(long nanos) {
        policyFetchTimer.record(nanos, TimeUnit.NANOSECONDS)
        meterRegistry.counter('cp.policies.fetch.result', 'outcome', 'error').increment()
    }

    def <T> T timePolicyPull(Closure<T> action) {
        long start = System.nanoTime()
        try {
            T result = action.call()
            recordPullSuccess(System.nanoTime() - start, result)
            return result
        } catch (Throwable throwable) {
            recordPullFailure(System.nanoTime() - start)
            throw throwable
        }
    }

    void recordPolicyDrift(String whiteLabelId) {
        meterRegistry.counter('cp.policies.drift', 'whiteLabelId', whiteLabelId ?: 'unknown').increment()
    }

    private void recordPullSuccess(long nanos, Object result) {
        policyPullTimer.record(nanos, TimeUnit.NANOSECONDS)
        meterRegistry.counter('cp.policies.pull.result', 'outcome', 'success').increment()
        int count = 0
        if (result instanceof Collection) {
            count = ((Collection) result).size()
        } else if (result != null) {
            count = 1
        }
        if (count > 0) {
            meterRegistry.counter('cp.policies.pull.count').increment(count)
        }
    }

    private void recordPullFailure(long nanos) {
        policyPullTimer.record(nanos, TimeUnit.NANOSECONDS)
        meterRegistry.counter('cp.policies.pull.result', 'outcome', 'error').increment()
    }
}
