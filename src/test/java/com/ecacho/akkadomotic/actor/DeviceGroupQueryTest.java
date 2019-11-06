package com.ecacho.akkadomotic.actor;

import static org.junit.Assert.*;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class DeviceGroupQueryTest {

  static ActorSystem actorSystem;

  @BeforeClass
  public static void setup() {
    actorSystem = ActorSystem.create();
  }

  @Test
  public void testReturnTemperatureValueForWorkingDevices() {
    TestKit requester = new TestKit(actorSystem);
    TestKit device1 = new TestKit(actorSystem);
    TestKit device2 = new TestKit(actorSystem);

    Map<ActorRef, String> map = new HashMap<>();
    map.put(device1.getRef(), "device1");
    map.put(device2.getRef(), "device2");

    //verify request Read Temperature
    ActorRef queryActor = actorSystem.actorOf(
        DeviceGroupQuery.props(
            map,
            1L,
            requester.getRef(),
            new FiniteDuration(3, TimeUnit.SECONDS)
        ));

    assertEquals(1L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
    assertEquals(1L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

    //simulate devices respond temperature

    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(20d)),
        device1.getRef());
    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(40d)),
        device2.getRef());

    //verify respond all temperatures
    DeviceGroup.RespondAllTemperatures response =
        requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);

    assertEquals(1L, response.requestId);


    Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new DeviceGroup.Temperature(20d));
    expectedTemperatures.put("device2", new DeviceGroup.Temperature(40d));

    assertEquals(expectedTemperatures, response.temperatures);
  }

  @Test
  public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
    TestKit requester = new TestKit(actorSystem);
    TestKit device1 = new TestKit(actorSystem);
    TestKit device2 = new TestKit(actorSystem);

    Map<ActorRef, String> map = new HashMap<>();
    map.put(device1.getRef(), "device1");
    map.put(device2.getRef(), "device2");

    //verify request Read Temperature
    ActorRef queryActor = actorSystem.actorOf(
        DeviceGroupQuery.props(
            map,
            1L,
            requester.getRef(),
            new FiniteDuration(3, TimeUnit.SECONDS)
        ));

    assertEquals(1L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
    assertEquals(1L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

    //simulate devices respond temperature

    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(20d)),
        device1.getRef());
    queryActor.tell(new Device.RespondTemperature(1L, Optional.empty()),
        device2.getRef());

    //verify respond all temperatures
    DeviceGroup.RespondAllTemperatures response =
        requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);

    assertEquals(1L, response.requestId);


    Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new DeviceGroup.Temperature(20d));
    expectedTemperatures.put("device2", DeviceGroup.TemperatureNotAvailable.INSTANCE);

    assertEquals(expectedTemperatures, response.temperatures);

  }

  @Test
  public void testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {

    TestKit requester = new TestKit(actorSystem);
    TestKit device1 = new TestKit(actorSystem);
    TestKit device2 = new TestKit(actorSystem);

    Map<ActorRef, String> map = new HashMap<>();
    map.put(device1.getRef(), "device1");
    map.put(device2.getRef(), "device2");

    //verify request Read Temperature
    ActorRef queryActor = actorSystem.actorOf(
        DeviceGroupQuery.props(
            map,
            1L,
            requester.getRef(),
            new FiniteDuration(3, TimeUnit.SECONDS)
        ));

    assertEquals(1L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
    assertEquals(1L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

    //simulate devices respond temperature

    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(20d)),
        device1.getRef());
    device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

    //verify respond all temperatures
    DeviceGroup.RespondAllTemperatures response =
        requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);

    assertEquals(1L, response.requestId);


    Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new DeviceGroup.Temperature(20d));
    expectedTemperatures.put("device2", DeviceGroup.DeviceNotAvailable.INSTANCE);

    assertEquals(expectedTemperatures, response.temperatures);
  }

  @Test
  public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
    TestKit requester = new TestKit(actorSystem);
    TestKit device1 = new TestKit(actorSystem);
    TestKit device2 = new TestKit(actorSystem);

    Map<ActorRef, String> map = new HashMap<>();
    map.put(device1.getRef(), "device1");
    map.put(device2.getRef(), "device2");

    //verify request Read Temperature
    ActorRef queryActor = actorSystem.actorOf(
        DeviceGroupQuery.props(
            map,
            1L,
            requester.getRef(),
            new FiniteDuration(3, TimeUnit.SECONDS)
        ));

    assertEquals(1L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
    assertEquals(1L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

    //simulate devices respond temperature

    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(20d)),
        device1.getRef());
    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(40d)),
        device2.getRef());
    device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

    //verify respond all temperatures
    DeviceGroup.RespondAllTemperatures response =
        requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);

    assertEquals(1L, response.requestId);


    Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new DeviceGroup.Temperature(20d));
    expectedTemperatures.put("device2", new DeviceGroup.Temperature(40d));

    assertEquals(expectedTemperatures, response.temperatures);
  }

  @Test
  public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
    TestKit requester = new TestKit(actorSystem);
    TestKit device1 = new TestKit(actorSystem);
    TestKit device2 = new TestKit(actorSystem);

    Map<ActorRef, String> map = new HashMap<>();
    map.put(device1.getRef(), "device1");
    map.put(device2.getRef(), "device2");

    //verify request Read Temperature
    ActorRef queryActor = actorSystem.actorOf(
        DeviceGroupQuery.props(
            map,
            1L,
            requester.getRef(),
            new FiniteDuration(1, TimeUnit.SECONDS)
        ));

    assertEquals(1L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
    assertEquals(1L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

    //simulate devices respond temperature

    queryActor.tell(new Device.RespondTemperature(1L, Optional.of(20d)),
        device1.getRef());
    device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

    //verify respond all temperatures
    DeviceGroup.RespondAllTemperatures response =
        requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);

    assertEquals(1L, response.requestId);
  }
}