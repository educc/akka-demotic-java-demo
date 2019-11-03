package com.ecacho.akkadomotic.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;

public class DeviceManager extends AbstractLoggingActor {


  final Map<String, ActorRef> groupIdToActor = new HashMap<>();
  final Map<ActorRef, String> actorToGroupId = new HashMap<>();

  public static Props props() {
    return Props.create(DeviceManager.class, DeviceManager::new);
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

  @AllArgsConstructor
  public static final class RequestTrackDevice {
    final String groupId;
    final String deviceId;
  }

  public static final class DeviceRegistered {

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
  public void preStart() {
    log().info("DeviceManager started");
  }

  @Override
  public void postStop() {
    log().info("DeviceManager stopped");
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(RequestTrackDevice.class, this::onTrackDevice)
        .match(Terminated.class, this::onTerminated)
        .build();
  }

  private void onTerminated(Terminated t) {
    ActorRef groupActor = t.getActor();

    String groupId = actorToGroupId.get(groupActor);

    log().info("Device group actor for {} has beend terminated", groupId);
    actorToGroupId.remove(groupActor);
    groupIdToActor.remove(groupId);
  }

  private void onTrackDevice(RequestTrackDevice trackMsg) {
    String groupId = trackMsg.groupId;
    ActorRef ref = groupIdToActor.get(groupId);
    if (ref != null) {
      ref.forward(trackMsg, getContext());
    } else {
      log().info("Creating device group actor for {}", groupId);

      ActorRef groupActor = getContext().actorOf(DeviceGroup.props(trackMsg.groupId));

      getContext().watch(groupActor);
      groupActor.forward(trackMsg, getContext());

      groupIdToActor.put(groupId, groupActor);
      actorToGroupId.put(groupActor, groupId);
    }
  }

}
