// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.tracing;


import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.cloudstack.api.APICommand;

import com.uber.jaeger.Configuration;
import com.uber.jaeger.samplers.ConstSampler;

import io.opentracing.Scope;

public class JaegarTracing implements MethodInterceptor {

    private final io.opentracing.Tracer tracer = initTracer("cloudstack");

    public com.uber.jaeger.Tracer initTracer(String service) {

        Configuration.SamplerConfiguration samplerConfig = Configuration.SamplerConfiguration.fromEnv()
                .withType(ConstSampler.TYPE)
                .withParam(1);

        Configuration.ReporterConfiguration reporterConfig = Configuration.ReporterConfiguration.fromEnv()
                .withLogSpans(true);

        Configuration config = new Configuration(service)
                .withSampler(samplerConfig)
                .withReporter(reporterConfig);

        return (com.uber.jaeger.Tracer) config.getTracer();
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        if (tracer.activeSpan() != null || methodInvocation.getMethod().getAnnotation(APICommand.class) != null) {
            try (Scope scope = tracer.buildSpan(methodInvocation.getMethod().getName()).startActive(true)) {
                scope.span().setTag("methodname", methodInvocation.getMethod().getName());
                return methodInvocation.proceed();
            }
        }
        return methodInvocation.proceed();
    }
}
