/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.example.hello.impl;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import akka.pattern.Backoff;
import akka.pattern.BackoffSupervisor;
import akka.util.Timeout;
import com.example.hello.api.GreetingMessage;
import com.example.hello.api.HelloService;
import com.example.hello.impl.HelloCommand.Hello;
import com.example.hello.impl.HelloCommand.UseGreetingMessage;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static akka.pattern.PatternsCS.ask;

/**
 * Implementation of the HelloService.
 */
public class HelloServiceImpl implements HelloService {

    private static final Timeout DEFAULT_TIMEOUT = new Timeout(new FiniteDuration(10, TimeUnit.SECONDS));

    private final ActorRef singletonProxy;

    @Inject
    public HelloServiceImpl(ActorSystem system, CassandraSession cassandraSession) {
        Props yourRealActorProps = PrepareDBActor.props(cassandraSession);

        Props backoffProps = BackoffSupervisor.props(
                Backoff.onFailure(
                        yourRealActorProps,
                        "your-real-actor",
                        Duration.create(3, TimeUnit.SECONDS),
                        Duration.create(30, TimeUnit.SECONDS),
                        0.2
                )
        );

        // Start the cluster singleton
        ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
        ActorRef singleton = system.actorOf(
                ClusterSingletonManager.props(backoffProps, PoisonPill.getInstance(), settings),
                "my-singleton"
        );

        ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system);
        singletonProxy = system.actorOf(
                ClusterSingletonProxy.props(
                        singleton.path().toStringWithoutAddress(),
                        proxySettings
                ),
                "my-singleton-proxy"
        );
    }

    @Override
    public ServiceCall<NotUsed, String> hello(String id) {
        return request -> {
            // Ask the singleton actor the Hello command.
            return ask(singletonProxy, new Hello(id, Optional.empty()), DEFAULT_TIMEOUT)
                    .thenApply(result -> (String) result);
        };
    }

    @Override
    public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
        return request -> {
            // Tell the singleton actor to use the greeting message specified.
            return ask(singletonProxy, new UseGreetingMessage(id, request.message), DEFAULT_TIMEOUT)
                    .thenApply(result -> Done.getInstance());
        };

    }

}
