package com.ecacho.akkadomotic.actor;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import java.util.Optional;
import lombok.AllArgsConstructor;

public class Device extends AbstractLoggingActor {
  //private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  final String groupId;
  final String deviceId;
  Optional<Double> lastTemperatureReading = Optional.empty();

  public Device(String groupId, String deviceId) {
    this.groupId = groupId;
    this.deviceId = deviceId;
  }

  public static Props props(String groupId, String deviceId) {
    return Props.create(Device.class, groupId, deviceId);
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
  public static final class ReadTemperature {
    final long requestId;
  }

  @AllArgsConstructor
  public static final class RespondTemperature {
    final long requestId;
    final Optional<Double> value;
  }

  @AllArgsConstructor
  public static final class RecordTemperature {
    final long requestId;
    final double value;
  }

  @AllArgsConstructor
  public static final class TemperatureRecorded {
    final long requestId;
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
    log().info("Device actor {}-{} started", groupId, deviceId);
  }

  @Override
  public void postStop() {
    log().info("Device actor {}-{} stopped", groupId, deviceId);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(DeviceManager.RequestTrackDevice.class, r -> {
          if (this.groupId.equals(r.groupId)
              && this.deviceId.equals(r.deviceId)) {
            getSender().tell(new DeviceManager.DeviceRegistered(), getSelf());
          } else {
            log().warning(
                "Ignoring TrackDevice request for {}-{}. this actor is "
                    + "responsible for {}-{}",
                r.groupId, r.deviceId, this.groupId, this.deviceId
            );
          }
        })
        .match(ReadTemperature.class, r -> {
          getSender().tell(
              new RespondTemperature(r.requestId, lastTemperatureReading),
              getSelf());
        })
        .match(RecordTemperature.class, r -> {
          log().info("Recorded temperature reading {} with {}", r.requestId, r.value);

          lastTemperatureReading = Optional.of(r.value);
          getSender().tell(new TemperatureRecorded(r.requestId), getSelf());
        })
        .build();
  }
}
