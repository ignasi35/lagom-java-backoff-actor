/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.example.hello.impl;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.pattern.Backoff;
import akka.pattern.BackoffSupervisor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.example.hello.api.GreetingMessage;
import com.example.hello.api.HelloService;
import com.example.hello.impl.HelloCommand.Hello;
import com.example.hello.impl.HelloCommand.UseGreetingMessage;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import scala.concurrent.duration.Duration;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the HelloService.
 */
public class HelloServiceImpl implements HelloService {

    private final PersistentEntityRegistry persistentEntityRegistry;

    @Inject
    public HelloServiceImpl(PersistentEntityRegistry persistentEntityRegistry, ActorSystem system, CassandraSession cassandraSession) {
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(HelloEntity.class);

        Props yourRealActorProps = PrepareDBActor.props(cassandraSession);

        Props targetActorProps = BackoffSupervisor.props(
                Backoff.onStop(
                        yourRealActorProps,
                        "your-real-actor",
                        Duration.create(3, TimeUnit.SECONDS),
                        Duration.create(30, TimeUnit.SECONDS),
                        0.2));

        ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
        ActorRef ref = system.actorOf(ClusterSingletonManager.props(targetActorProps, akka.actor.PoisonPill.getInstance(), settings), "my-supervisor");

        Patterns.ask(ref, new PrepareDBActor.Execute(), Timeout.apply(10, TimeUnit.SECONDS));

    }

    @Override
    public ServiceCall<NotUsed, String> hello(String id) {
        return request -> {
            // Look up the hello world entity for the given ID.
            PersistentEntityRef<HelloCommand> ref = persistentEntityRegistry.refFor(HelloEntity.class, id);
            // Ask the entity the Hello command.
            return ref.ask(new Hello(id, Optional.empty()));
            //return CompletableFuture.completedFuture("Hello someone");
        };
    }

    @Override
    public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
        return request -> {
            // Look up the hello world entity for the given ID.
            PersistentEntityRef<HelloCommand> ref = persistentEntityRegistry.refFor(HelloEntity.class, id);
            // Tell the entity to use the greeting message specified.
            return ref.ask(new UseGreetingMessage(request.message));
        };

    }

}
