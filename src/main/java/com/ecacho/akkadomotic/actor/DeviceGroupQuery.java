package com.ecacho.akkadomotic.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Terminated;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import scala.concurrent.duration.FiniteDuration;

public class DeviceGroupQuery extends AbstractLoggingActor {

  final Map<ActorRef, String> actorToDeviceId;
  final Long requestId;
  final ActorRef requester;
  Cancellable queryTimeoutTimer;

  public DeviceGroupQuery(
      Map<ActorRef, String> actorToDeviceId,
      Long requestId,
      ActorRef requester,
      FiniteDuration timeout) {
    this.actorToDeviceId = actorToDeviceId;
    this.requestId = requestId;
    this.requester = requester;

    queryTimeoutTimer = getContext()
          .getSystem()
          .getScheduler()
          .scheduleOnce(
              timeout,
              getSelf(),
              new CollectionTimeout(),
              getContext().getDispatcher(),
              getSelf()
          );

  }

  public static Props props(
      Map<ActorRef, String> actorToDeviceId,
      Long requestId,
      ActorRef requester,
      FiniteDuration timeout) {
    return Props.create(DeviceGroupQuery.class,
        () -> new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout));
  }

  /*

  ########  ########   #######  ########  #######   ######   #######  ##
  ##     ## ##     ## ##     ##    ##    ##     ## ##    ## ##     ## ##
  ##     ## ##     ## ##     ##    ##    ##     ## ##       ##     ## ##
  ########  ########  ##     ##    ##    ##     ## ##       ##     ## ##
  ##        ##   ##   ##     ##    ##    ##     ## ##       ##     ## ##
  ##        ##    ##  ##     ##    ##    ##     ## ##    ## ##     ## ##
  ##        ##     ##  #######     ##     #######   ######   #######  ########

   */

  public static final class CollectionTimeout {

  }




  /*

  ########  ######## ##     ##    ###    ##     ## ####  #######  ########
  ##     ## ##       ##     ##   ## ##   ##     ##  ##  ##     ## ##     ##
  ##     ## ##       ##     ##  ##   ##  ##     ##  ##  ##     ## ##     ##
  ########  ######   ######### ##     ## ##     ##  ##  ##     ## ########
  ##     ## ##       ##     ## #########  ##   ##   ##  ##     ## ##   ##
  ##     ## ##       ##     ## ##     ##   ## ##    ##  ##     ## ##    ##
  ########  ######## ##     ## ##     ##    ###    ####  #######  ##     ##

   */

  @Override
  public void preStart()  {
    for (ActorRef deviceActor : actorToDeviceId.keySet()) {
      getContext().watch(deviceActor);
      deviceActor.tell(new Device.ReadTemperature(requestId), getSelf());
    }
  }

  @Override
  public void postStop()  {
    queryTimeoutTimer.cancel();
  }

  @Override
  public Receive createReceive() {
    return waitingForReplies(new HashMap<String, DeviceGroup.TemperatureReading>(),
        actorToDeviceId.keySet());
  }

  private Receive waitingForReplies(
      HashMap<String, DeviceGroup.TemperatureReading> repliesSoFar,
      Set<ActorRef> stillWaiting) {
    return receiveBuilder()
        .match(
            Device.RespondTemperature.class,
            r -> {
              ActorRef deviceActor = getSender();
              DeviceGroup.TemperatureReading reading =
                  r.value
                      .map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.Temperature(v))
                      .orElse(DeviceGroup.TemperatureNotAvailable.INSTANCE);
              receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);
            })
        .match(
            Terminated.class,
            t -> {
              receivedResponse(
                  t.getActor(),
                  DeviceGroup.DeviceNotAvailable.INSTANCE,
                  stillWaiting,
                  repliesSoFar);
            })
        .match(
            CollectionTimeout.class,
            t -> {
              Map<String, DeviceGroup.TemperatureReading> replies = new HashMap<>(repliesSoFar);
              for (ActorRef deviceActor : stillWaiting) {
                String deviceId = actorToDeviceId.get(deviceActor);
                replies.put(deviceId, DeviceGroup.DeviceTimeout.INSTANCE);
              }
              requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replies), getSelf());
              getContext().stop(getSelf());
            })
        .build();

  }

  private void receivedResponse(
      ActorRef deviceActor,
      DeviceGroup.TemperatureReading reading,
      Set<ActorRef> stillWaiting, HashMap<String,
      DeviceGroup.TemperatureReading> repliesSoFar) {

    getContext().unwatch(deviceActor);
    String deviceId = actorToDeviceId.get(deviceActor);

    HashSet<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
    newStillWaiting.remove(deviceActor);

    HashMap<String, DeviceGroup.TemperatureReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
    newRepliesSoFar.put(deviceId, reading);

    if (newStillWaiting.isEmpty()) {
      requester.tell(new DeviceGroup.RespondAllTemperatures(
          requestId,
          newRepliesSoFar
      ), getSelf());
      getContext().stop(getSelf());
    } else {
      getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
    }
  }

}
