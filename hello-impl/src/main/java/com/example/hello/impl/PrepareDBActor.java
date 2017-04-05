package com.example.hello.impl;

import akka.Done;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.UntypedActorWithStash;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import akka.pattern.PatternsCS;
import akka.pattern.PipeToSupport;
import com.example.hello.impl.HelloCommand.Hello;
import com.example.hello.impl.HelloCommand.UseGreetingMessage;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;

import java.util.concurrent.CompletionStage;


public class PrepareDBActor extends UntypedActorWithStash {

    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private final CassandraSession cassandraSession;

    public static Props props(CassandraSession cassandraSession) {
        return Props.create(PrepareDBActor.class, cassandraSession);
    }

    PrepareDBActor(CassandraSession cassandraSession) {
        this.cassandraSession = cassandraSession;
    }

    @Override
    public void preStart() {
        log.info("Creating table: greeting");
        CompletionStage<Done> createTable =
                cassandraSession.executeCreateTable("CREATE TABLE IF NOT EXISTS greeting (id text PRIMARY KEY, message text);");
        pipe(createTable).to(getSelf());
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof Done) {
            log.info("Done creating table: greeting");
            unstashAll();
            getContext().become(active);
        } else if (message instanceof Status.Failure) {
            Status.Failure failure = (Status.Failure) message;
            Throwable error = failure.cause();
            log.error(error, "Exception when creating table: greeting; will retry");
            throw error;
        } else {
            stash();
        }
    }

    private final Procedure<Object> active = message -> {
        if (message instanceof Hello) {
            Hello hello = (Hello) message;
            pipe(getGreetingMessage(hello.name)).to(getSender());
        } else if (message instanceof UseGreetingMessage) {
            UseGreetingMessage useGreetingMessage = (UseGreetingMessage) message;
            pipe(setGreetingMessage(useGreetingMessage.id, useGreetingMessage.message)).to(getSender());
        } else unhandled(message);
    };


    private CompletionStage<String> getGreetingMessage(String id) {
        log.info("Getting greeting message for [{}]", id);
        return cassandraSession.selectOne("SELECT * FROM greeting WHERE id = ?", id)
                .thenApply(row ->
                        row.map(result -> result.getString("message")).orElse("Hello")
                )
                .thenApply(message -> message + ", " + id + "!");
    }

    private CompletionStage<Done> setGreetingMessage(String id, String message) {
        log.info("Setting greeting message for [{}] to [{}]", id, message);
        return cassandraSession.executeWrite("UPDATE greeting SET message = ? WHERE id = ?", message, id);
    }

    private <T> PipeToSupport.PipeableCompletionStage<T> pipe(CompletionStage<T> completionStage) {
        return PatternsCS.pipe(completionStage, getContext().dispatcher());
    }

    @Override
    public void postStop() {
        log.info("PrepareDBActor stopped");
        super.postStop();
    }
}
