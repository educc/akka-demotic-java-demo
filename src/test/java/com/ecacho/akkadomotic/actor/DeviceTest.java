package com.ecacho.akkadomotic.actor;

import static org.junit.Assert.*;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestKit;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

public class DeviceTest {
  static ActorSystem actorSystem;

  @BeforeClass
  public static void setup() {
    actorSystem = ActorSystem.create();
  }

  /*@AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(actorSystem, Duration.create("10 seconds"), true);
    actorSystem = null;
  }*/

  @Test
  public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
    TestKit probe = new TestKit(actorSystem);

    ActorRef deviceActor =
        actorSystem.actorOf(Device.props("group", "device"));
    deviceActor.tell(new Device.ReadTemperature(42L), probe.testActor());

    Device.RespondTemperature response =
        probe.expectMsgClass(Device.RespondTemperature.class);

    assertEquals(42L, response.requestId);
    assertEquals(Optional.empty(), response.value);
  }

  @Test
  public void testReplyWithLatestTemperatureReading() {
    TestKit probe = new TestKit(actorSystem);

    ActorRef deviceActor =
        actorSystem.actorOf(Device.props("group", "device"));

    //First record and read
    deviceActor.tell(new Device.RecordTemperature(1L, 24.0), probe.testActor());

    assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

    deviceActor.tell(new Device.ReadTemperature(2L), probe.testActor());
    Device.RespondTemperature res1 = probe.expectMsgClass(Device.RespondTemperature.class);

    assertEquals(2L, res1.requestId);
    assertEquals(Optional.of(24.0), res1.value);

    //second record and read

    deviceActor.tell(new Device.RecordTemperature(3L, 55.0), probe.testActor());

    assertEquals(3L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

    deviceActor.tell(new Device.ReadTemperature(4L), probe.testActor());
    Device.RespondTemperature res2 = probe.expectMsgClass(Device.RespondTemperature.class);

    assertEquals(4L, res2.requestId);
    assertEquals(Optional.of(55.0), res2.value);
  }

  @Test
  public void testReplyToRegistrationRequests() {
    TestKit probe = new TestKit(actorSystem);

    ActorRef deviceActor =
        actorSystem.actorOf(Device.props("group", "device"));

    deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "device"),
        probe.testActor());

    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

    assertEquals(deviceActor, probe.lastSender());
  }

  @Test
  public void testIgnoreWornRegistrationRequests() {
    TestKit probe = new TestKit(actorSystem);

    ActorRef deviceActor =
        actorSystem.actorOf(Device.props("group", "device"));

    //First record and read
    deviceActor.tell(new DeviceManager.RequestTrackDevice("unknow", "unknow"),
        probe.testActor());
    probe.expectNoMsg();


    deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "unknow"),
        probe.testActor());
    probe.expectNoMsg();
  }

}