package com.ecacho.akkadomotic.actor;

import static org.junit.Assert.*;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeviceGroupTest {
  static ActorSystem actorSystem;

  @BeforeClass
  public static void setup() {
    actorSystem = ActorSystem.create();
  }

  @Test
  public void testRegisterDeviceActor() {
    TestKit probe = new TestKit(actorSystem);
    ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));

    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor1 = probe.getLastSender();

    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor2 = probe.getLastSender();
    assertNotEquals(deviceActor1, deviceActor2);

    // Check that the device actors are working
    deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
    assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

    deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
    assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
  }

  @Test
  public void testIgnoreRequestsForWrongGroupId() {
    TestKit probe = new TestKit(actorSystem);
    ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));

    groupActor.tell(new DeviceManager.RequestTrackDevice("unknown", "device1"),
        probe.getRef());
    probe.expectNoMessage();
  }

  @Test
  public void testReturnSameActorForSameDeviceId() {
    TestKit probe = new TestKit(actorSystem);
    ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));


    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor1 = probe.getLastSender();


    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor2 = probe.getLastSender();

    assertEquals(deviceActor1, deviceActor2);
  }

  @Test
  public void testListActiveDevices() {
    TestKit probe = new TestKit(actorSystem);
    ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));


    //register devices
    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);


    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

    //retrieve devices
    groupActor.tell(new DeviceGroup.RequestDeviceList(1L), probe.getRef());
    DeviceGroup.ReplyDeviceList replyDeviceList =
        probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);

    assertEquals(1L, replyDeviceList.requestId);
    assertEquals(
        Stream.of("device1", "device2").collect(Collectors.toSet()),
        replyDeviceList.list
    );
  }

  @Test
  public void testListActiveDevicesAfterOneShutdown() {
    TestKit probe = new TestKit(actorSystem);
    ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));

    //register devices
    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef actorToShutDown = probe.getLastSender();


    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"),
        probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

    //retrieve devices
    groupActor.tell(new DeviceGroup.RequestDeviceList(1L), probe.getRef());
    DeviceGroup.ReplyDeviceList replyDeviceList =
        probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);

    assertEquals(1L, replyDeviceList.requestId);
    assertEquals(
        Stream.of("device1", "device2").collect(Collectors.toSet()),
        replyDeviceList.list
    );

    //shutdown one actor
    probe.watch(actorToShutDown);
    actorToShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
    probe.expectTerminated(actorToShutDown);

    // using awaitAssert to retry because it might take longer for the groupActor
    // to see the Terminated, that order is undefined

    probe.awaitAssert(
        () -> {
          groupActor.tell(new DeviceGroup.RequestDeviceList(2L), probe.getRef());
          DeviceGroup.ReplyDeviceList r = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
          assertEquals(2L, r.requestId);
          assertEquals(Stream.of("device2").collect(Collectors.toSet()), r.list);
          return null;
        });

  }
}