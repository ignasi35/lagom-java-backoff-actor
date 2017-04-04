package com.example.hello.impl;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.persistence.cassandra.session.javadsl.CassandraSession;


public class PrepareDBActor extends UntypedActor {

    private CassandraSession cassandraSession;

    public static Props props(CassandraSession cassandraSession){
        return Props.create(PrepareDBActor.class, cassandraSession);
    }
    static public class Execute {
        public Execute() {}
    }

    PrepareDBActor(CassandraSession cassandraSession){
        this.cassandraSession = cassandraSession;
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof Execute) {
            cassandraSession
                    .executeCreateTable("CREATE TABLE IF NOTE EXISTS names (name text) ;")
                    .thenAccept(done -> sender().tell(done, self()));
        } else
            unhandled(message);
    }
}