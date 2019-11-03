package com.ecacho.akkadomotic.actor;

import static org.junit.Assert.*;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import java.util.Optional;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeviceManagerTest {

  static ActorSystem actorSystem;

  @BeforeClass
  public static void setup() {
    actorSystem = ActorSystem.create();
  }

  @Test
  public void createGroupDeviceTest() {
    TestKit probe = new TestKit(actorSystem);
    ActorRef deviceManagerActor = actorSystem.actorOf(DeviceManager.props());

    deviceManagerActor.tell(
        new DeviceManager.RequestTrackDevice("group1", "device1"),
        probe.getRef());


    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor = probe.getLastSender();

    deviceActor.tell(new Device.ReadTemperature(1L), probe.getRef());
    Device.RespondTemperature replyTemperature =
        probe.expectMsgClass(Device.RespondTemperature.class);

    assertEquals(1L, replyTemperature.requestId);
    assertEquals(Optional.empty(), replyTemperature.value);


  }

}