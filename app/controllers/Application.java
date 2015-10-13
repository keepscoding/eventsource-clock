package controllers;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.google.inject.Singleton;
import play.Logger;
import play.libs.Akka;
import play.libs.EventSource;
import play.mvc.Controller;
import play.mvc.Result;
import scala.concurrent.duration.Duration;
import views.html.index;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static play.libs.EventSource.Event.event;

@Singleton
public class Application extends Controller {

    final ActorRef clock;

    public  Result index() {
        return ok(index.render());
    }

    public Application() {
        clock = Clock.instance;
    }
    public Result liveClock() {
        return ok(new EventSource() {
            public void onConnected() {
                clock.tell(this, null);
            }
        });
    }

    public static class Clock extends UntypedActor {

        static ActorRef instance = Akka.system().actorOf(Props.create(Clock.class));

        // Send a TICK message every 100 millis
        static {
            Akka.system().scheduler().schedule(
                    Duration.Zero(),
                    Duration.create(100, MILLISECONDS),
                    instance, "TICK", Akka.system().dispatcher(),
                    null
            );
        }

        List<EventSource> sockets = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH mm ss");

        public void onReceive(Object message) {

            // Handle connections
            if (message instanceof EventSource) {
                final EventSource eventSource = (EventSource) message;

                if (sockets.contains(eventSource)) {
                    // Browser is disconnected
                    sockets.remove(eventSource);
                    Logger.info("Browser disconnected (" + sockets.size() + " browsers currently connected)");

                } else {
                    // Register disconnected callback
                    eventSource.onDisconnected(() -> getContext().self().tell(eventSource, null));
                    // New browser connected
                    sockets.add(eventSource);
                    Logger.info("New browser connected (" + sockets.size() + " browsers currently connected)");

                }

            }
            // Tick, send time to all connected browsers
            if ("TICK".equals(message)) {
                // Send the current time to all EventSource sockets
                List<EventSource> shallowCopy = new ArrayList<>(sockets); //prevent ConcurrentModificationException
                for (EventSource es : shallowCopy) {
                    es.send(event(dateFormat.format(new Date())));
                }

            }

        }

    }
}
