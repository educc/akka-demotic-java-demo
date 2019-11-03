package com.ecacho.akkadomotic.actor;


import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

public class DeviceGroup extends AbstractLoggingActor {

  final String groupId;
  final Map<String, ActorRef> deviceIdToActor = new HashMap<>();
  final Map<ActorRef, String> actorToDeviceId = new HashMap<>();

  public DeviceGroup(String groupId) {
    this.groupId = groupId;
  }

  public static Props props(String groupId) {
    return Props.create(DeviceGroup.class, groupId);
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
  public static final class RequestDeviceList {
    final Long requestId;
  }

  @AllArgsConstructor
  public static final class ReplyDeviceList {
    final long requestId;
    final Set<String> list;
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
    log().info("DeviceGroup {} started", groupId);
  }

  @Override
  public void postStop() {
    log().info("DeviceGroup {} stopped", groupId);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
        .match(RequestDeviceList.class, this::onDeviceList)
        .match(Terminated.class, this::onTerminated)
        .build();
  }

  private void onTrackDevice(DeviceManager.RequestTrackDevice trackMsg) {
    if (this.groupId.equals(trackMsg.groupId)) {
      ActorRef deviceActor = deviceIdToActor.get(trackMsg.deviceId);

      if (deviceActor!= null) {
        deviceActor.forward(trackMsg, getContext());
      } else {
        log().info("Creating device actor for {}", trackMsg.deviceId);

        deviceActor = getContext().actorOf(
            Device.props(groupId, trackMsg.deviceId),
            "device-" + trackMsg.deviceId);

        getContext().watch(deviceActor);
        deviceIdToActor.put(trackMsg.deviceId, deviceActor);
        actorToDeviceId.put(deviceActor, trackMsg.deviceId);

        deviceActor.forward(trackMsg, getContext());
      }
    } else {
      log().warning(
          "Ignoring TrackDevice request for {}. this actor is responsible for {}.",
          trackMsg.groupId, this.groupId
      );
    }
  }

  private void onTerminated(Terminated t) {
    ActorRef deviceActor = t.getActor();

    String deviceId = actorToDeviceId.get(deviceActor);

    log().info("Device actor for {} has been terminated", deviceId);

    actorToDeviceId.remove(deviceActor);
    deviceIdToActor.remove(deviceId);
  }

  private void onDeviceList(RequestDeviceList rq) {
    getSender().tell(
        new ReplyDeviceList(rq.requestId, deviceIdToActor.keySet()),
        getSelf()
    );
  }
}
