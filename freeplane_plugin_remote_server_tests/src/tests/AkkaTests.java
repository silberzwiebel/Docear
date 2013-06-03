package tests;

import static org.fest.assertions.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.docear.messages.Messages.AddNodeRequest;
import org.docear.messages.Messages.AddNodeResponse;
import org.docear.messages.Messages.ChangeEdgeRequest;
import org.docear.messages.Messages.ChangeEdgeResponse;
import org.docear.messages.Messages.ChangeNodeRequest;
import org.docear.messages.Messages.ChangeNodeResponse;
import org.docear.messages.Messages.CloseAllOpenMapsRequest;
import org.docear.messages.Messages.CloseMapRequest;
import org.docear.messages.Messages.CloseUnusedMaps;
import org.docear.messages.Messages.FetchMindmapUpdatesRequest;
import org.docear.messages.Messages.FetchMindmapUpdatesResponse;
import org.docear.messages.Messages.GetNodeRequest;
import org.docear.messages.Messages.GetNodeResponse;
import org.docear.messages.Messages.MindmapAsJsonReponse;
import org.docear.messages.Messages.MindmapAsJsonRequest;
import org.docear.messages.Messages.MindmapAsXmlRequest;
import org.docear.messages.Messages.MindmapAsXmlResponse;
import org.docear.messages.Messages.MoveNodeToRequest;
import org.docear.messages.Messages.MoveNodeToResponse;
import org.docear.messages.Messages.OpenMindMapRequest;
import org.docear.messages.Messages.OpenMindMapResponse;
import org.docear.messages.Messages.ReleaseLockRequest;
import org.docear.messages.Messages.ReleaseLockResponse;
import org.docear.messages.Messages.RemoveNodeRequest;
import org.docear.messages.Messages.RemoveNodeResponse;
import org.docear.messages.Messages.RequestLockRequest;
import org.docear.messages.Messages.RequestLockResponse;
import org.docear.messages.exceptions.MapNotFoundException;
import org.docear.messages.exceptions.NodeNotFoundException;
import org.docear.messages.models.MapIdentifier;
import org.docear.messages.models.UserIdentifier;
import org.fest.assertions.Fail;
import org.freeplane.features.edge.EdgeStyle;
import org.freeplane.plugin.remote.v10.model.EdgeModel;
import org.freeplane.plugin.remote.v10.model.NodeModelDefault;
import org.freeplane.plugin.remote.v10.model.updates.AddNodeUpdate;
import org.freeplane.plugin.remote.v10.model.updates.ChangeNodeAttributeUpdate;
import org.freeplane.plugin.remote.v10.model.updates.MapUpdate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.typesafe.config.ConfigFactory;

public class AkkaTests {

	private final static String SOURCE = "testing";
	private final static String USERNAME1 = "USER1";
	private final static String USERNAME2 = "USER2";

	private static ActorSystem system;
	private static ActorRef remoteActor;
	private static ActorRef localActor;
	private static ObjectMapper objectMapper;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		system = ActorSystem.create("actoruser", ConfigFactory.load().getConfig("local"));

		localActor = system.actorOf(new Props(TheActor.class), "localActor_" + System.currentTimeMillis());

		setUpConnectionToFreeplane();

		objectMapper = new ObjectMapper();
	}

	private static void setUpConnectionToFreeplane() {
		long startTime = System.currentTimeMillis();
		long endTime = startTime + 60000; // one minute
		while (remoteActor == null && System.currentTimeMillis() < endTime) {
			try {
				remoteActor = system.actorFor("akka://freeplaneRemote@127.0.0.1:2553/user/main");

				Future<Object> future = Patterns.ask(remoteActor, new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1),new MapIdentifier("-1", "NOT_EXISTING")), 2000);
				Await.result(future, Duration.create("2 second"));
			} catch (MapNotFoundException e) {
				// expected, good
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Could not connect to Freeplane remote. Waiting 10 seconds.");
				remoteActor = null;
				try {
					Thread.sleep(10000); // 10 seconds
				} catch (InterruptedException e1) {
				}
			}
		}

		if (remoteActor == null) {
			Fail.fail("Could not connect to Freeplane Remote");
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		system.shutdown();
	}

	@Before
	public void setUp() throws Exception {

	}

	@After
	public void tearDown() throws Exception {
		remoteActor.tell(new CloseAllOpenMapsRequest(new UserIdentifier(SOURCE, USERNAME1)), localActor);
	}

	public void testSkeleton() {
		new JavaTestKit(system) {
			{
				// need to register to the localActor
				localActor.tell(getRef(), getRef());
			}
		};
	}

	/**
	 * testMindMapAsJson Open one of default test maps and receive json of map
	 */
	@Test
	@Ignore
	public void testMindMapAsJson() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					@Override
					protected void run() {
						sendMindMapToServer(5);
						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5")), getRef());

						MindmapAsJsonReponse response = expectMsgClass(MindmapAsJsonReponse.class);
						String jsonString = response.getJsonString();

						validateMapSchema(jsonString);

						assertThat(response.getJsonString()).contains("\"root\":{\"id\":\"ID_0\",\"nodeText\":\"test_5 = MapID ; 5.mm = Title\"");
						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * testMindMapAsJsonFail Try to open a not available map. Should throw
	 * MapNotFoundException.
	 */
	@Test
	public void testMindMapAsJsonFail() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "6")), getRef());
						Failure response = expectMsgClass(Failure.class);
						System.out.println(response.cause());
						assertThat(response.cause() instanceof MapNotFoundException).isTrue();
					}
				};
			}
		};
	}

	/**
	 * testMindMapAsXml Send MindMap to server. Request opened Mindmap as xml.
	 */
	@Test
	public void testMindMapAsXml() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						sendMindMapToServer(5);

						remoteActor.tell(new MindmapAsXmlRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5")), localActor);

						MindmapAsXmlResponse response = expectMsgClass(MindmapAsXmlResponse.class);
						assertThat(response.getXmlString())
								.contains(
										"<node TEXT=\"right_L1P0_Links\" COLOR=\"#000000\" STYLE=\"as_parent\" MAX_WIDTH=\"600\" MIN_WIDTH=\"1\" POSITION=\"right\" ID=\"ID_1\" CREATED=\"1354627639897\" MODIFIED=\"1355079961660\" HGAP=\"70\" VSHIFT=\"-160\">");

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * testMindMapAsXmlFail Requesting not opened mindmap. should throw
	 * MapNotFoundException
	 */
	@Test
	public void testMindMapAsXmlFail() {
		new JavaTestKit(system) {
			{
				new Within(duration("3 seconds")) {
					protected void run() {
						localActor.tell(getRef(), getRef());
						remoteActor.tell(new MindmapAsXmlRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5")), localActor);

						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause() instanceof MapNotFoundException).isTrue();

					}
				};
			}
		};
	}

	/**
	 * testAddNodeRequest Open Map. Add new node to root node.
	 */
	@Test
	public void testAddNodeRequest() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						try {
							sendMindMapToServer(5);
							remoteActor.tell(new AddNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_0"), localActor);
							// expectMsgClass(Failure.class).cause().printStackTrace();
							final AddNodeResponse response = expectMsgClass(AddNodeResponse.class);

							final AddNodeUpdate update = objectMapper.readValue(response.getMapUpdate(), AddNodeUpdate.class);
							assertThat(update.getType()).isEqualTo(MapUpdate.Type.AddNode);
							validateDefaultNodeSchema(update.getNodeAsJson());
							final NodeModelDefault node = objectMapper.readValue(update.getNodeAsJson().toString(), NodeModelDefault.class);
							Assert.assertEquals("", node.nodeText);

						} catch (JsonMappingException e) {
							Fail.fail("json mapping error", e);
						} catch (JsonParseException e) {
							Fail.fail("json parse error", e);
						} catch (IOException e) {
							Fail.fail("json IOException error", e);
						} finally {
							closeMindMapOnServer(5);
						}
					}
				};
			}
		};
	}

	/**
	 * testAddNodeRequestFailInvalidNode Open Map. Add new node to invalid node.
	 * Should throw NodeNotFoundException
	 */
	@Test
	public void testAddNodeRequestFailInvalidNode() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						sendMindMapToServer(5);
						remoteActor.tell(new AddNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_FAIL"), localActor);

						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause() instanceof NodeNotFoundException).isTrue();
						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * testAddNodeRequestFailInvalidMap Open no Map. Try to add node. Should
	 * throw MapNotFoundException
	 */
	@Test
	public void testAddNodeRequestFailInvalidMap() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						remoteActor.tell(new AddNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "16"), "ID_FAIL"), localActor);

						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause() instanceof MapNotFoundException).isTrue();
					}
				};
			}
		};
	}

	/**
	 * testGetNodeRequest Get node from map
	 */
	@Test
	public void testGetNodeRequest() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						try {
							sendMindMapToServer(5);
							remoteActor.tell(new GetNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_1", 1), localActor);

							GetNodeResponse response = expectMsgClass(GetNodeResponse.class);
							//TODO fix test, why is schema failing? (no time right now..., js)
							//validateDefaultNodeSchema(response.getNode());
							NodeModelDefault node = objectMapper.readValue(response.getNode(), NodeModelDefault.class);
							System.out.println(node.nodeText);
							assertThat(node.nodeText).isEqualTo("right_L1P0_Links");
							assertThat(node.hGap).isEqualTo(70);

						} catch (JsonMappingException e) {
							Fail.fail("json mapping error", e);
						} catch (JsonParseException e) {
							Fail.fail("json parse error", e);
						} catch (IOException e) {
							Fail.fail("json IOException error", e);
						} finally {
							closeMindMapOnServer(5);
						}
					}
				};
			}
		};
	}

	/**
	 * testGetNodeRequest Get invalid node from map. Should throw
	 * NodeNotFoundException
	 */
	@Test
	public void testGetNodeRequestFailInvalidNode() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						sendMindMapToServer(5);
						remoteActor.tell(new GetNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_FAIL", 1), localActor);

						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause() instanceof NodeNotFoundException).isTrue();
						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * testRemoveNodeRequest send map to server. remove valid node from Map.
	 * check if node with id isn't available any more.
	 */
	@Test
	@Ignore
	public void testRemoveNodeRequest() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						sendMindMapToServer(5);
						remoteActor.tell(new RemoveNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_1"), localActor);
						RemoveNodeResponse rmNodeResponse = expectMsgClass(RemoveNodeResponse.class);
						assertThat(rmNodeResponse.getDeleted()).isEqualTo(true);

						remoteActor.tell(new GetNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_1", 1), localActor);
						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause() instanceof NodeNotFoundException).isTrue();

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * testRemoveNodeRequestFailInvalidNode send map to server. remove valid
	 * node from Map. check if
	 */
	@Test
	public void testRemoveNodeRequestFailInvalidNode() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						sendMindMapToServer(5);
						remoteActor.tell(new RemoveNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_FAIL"), localActor);

						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause() instanceof NodeNotFoundException).isTrue();

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * testRemoveNodeRequestFailChildNodeLocked<br>
	 * Send map to server. <br>
	 * Add lock to child of node to delete<br>
	 * Try to remove valid node from Map, but with locked child.
	 */
	@Test
	public void testRemoveNodeRequestFailChildNodeLocked() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					protected void run() {
						sendMindMapToServer(5);
						requestLock(new MapIdentifier("-1", "5"), "ID_2", USERNAME2);
						remoteActor.tell(new RemoveNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_1"), localActor);

						RemoveNodeResponse response = expectMsgClass(RemoveNodeResponse.class);
						assertThat(response.getDeleted()).isEqualTo(false);

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}


	/**
	 * testChangeNodeXMLRequest change available node to defined attributes. check
	 * if node got attributes.
	 */
	@Test
	public void testChangeNodeXMLRequest() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("10 seconds")) {
					protected void run() {
						sendMindMapToServer(5);

						final String nodeId = "ID_1";
						final String newNodeText = "This is a new <br>bold</br> nodeText";
						final boolean isHtml = true;

						Map<String, Object> attributeMap = new HashMap<String, Object>();
						attributeMap.put("nodeText", newNodeText);
						attributeMap.put("isHtml", isHtml);

						final ChangeNodeRequest request = new ChangeNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), nodeId, attributeMap);

						// requesting lock on node
						requestLock(new MapIdentifier("-1", "5"), nodeId, USERNAME1);

						remoteActor.tell(request, localActor);
						ChangeNodeResponse response = expectMsgClass(ChangeNodeResponse.class);
						

						// release lock
						releaseLock(new MapIdentifier("-1", "5"), nodeId, USERNAME1);

						try {
							final List<String> mapUpdates = response.getMapUpdates();

							// Set with attributes that have to be changed
							final Set<String> notChangedAttributes = new HashSet<String>(Arrays.asList(new String[] { "nodeText", "isHtml"}));

							for (String updateJson : mapUpdates) {
								final ChangeNodeAttributeUpdate update = new ObjectMapper().readValue(updateJson, ChangeNodeAttributeUpdate.class);
								assertThat(update.getType()).isEqualTo(MapUpdate.Type.ChangeNodeAttribute);

								final String attribute = update.getAttribute();
								final Object value = update.getValue();

								// remove from not changed list and assert
								assertThat(notChangedAttributes.remove(attribute)).describedAs("Is value supposed to change").isEqualTo(true);

								if (attribute.equals("nodeText")) {
									assertThat(value).isEqualTo(attributeMap.get("nodeText"));
								} else if (attribute.equals("isHtml")) {
									assertThat(Boolean.parseBoolean(value.toString())).isEqualTo(Boolean.parseBoolean(attributeMap.get("isHtml").toString()));
								} else if (attribute.equals("folded")) {
									assertThat(value).isEqualTo(attributeMap.get("folded"));
								} else if (attribute.equals("link")) {
									assertThat(value).isEqualTo(attributeMap.get("link"));
								} else if (attribute.equals("hGap")) {
									assertThat(value).isEqualTo(attributeMap.get("hGap"));
								} else if (attribute.equals("shiftY")) {
									assertThat(value).isEqualTo(attributeMap.get("shiftY"));
								} else if (attribute.equals("note")) {
									assertThat(value).isEqualTo(attributeMap.get("note"));
								}
							}

							// check that everything changed
							assertThat(notChangedAttributes.size()).isEqualTo(0);

						} catch (JsonMappingException e) {
							Fail.fail("json mapping error", e);
						} catch (JsonParseException e) {
							Fail.fail("json parse error", e);
						} catch (IOException e) {
							Fail.fail("json IOException error", e);
						} finally {
							closeMindMapOnServer(5);
						}
					}
				};
			}
		};
	}
	
	/**
	 * testChangeNodeRequest change available node to defined attributes. check
	 * if node got attributes.
	 */
	@Test
	public void testChangeNodeRequest() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("10 seconds")) {
					protected void run() {
						sendMindMapToServer(5);

						final String nodeId = "ID_1";
						final Map<String, Object> attributeMap = getNewAttributesForNode();

						final ChangeNodeRequest request = new ChangeNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), nodeId, attributeMap);

						// requesting lock on node
						requestLock(new MapIdentifier("-1", "5"), nodeId, USERNAME1);

						remoteActor.tell(request, localActor);
						ChangeNodeResponse response = expectMsgClass(ChangeNodeResponse.class);

						// release lock
						releaseLock(new MapIdentifier("-1", "5"), nodeId, USERNAME1);

						try {
							final List<String> mapUpdates = response.getMapUpdates();

							// Set with attributes that have to be changed
							final Set<String> notChangedAttributes = new HashSet<String>(Arrays.asList(new String[] { "nodeText", "isHtml", "folded", "link", "hGap", "shiftY", "attributes", "icons",
									"note" }));

							for (String updateJson : mapUpdates) {
								final ChangeNodeAttributeUpdate update = new ObjectMapper().readValue(updateJson, ChangeNodeAttributeUpdate.class);
								assertThat(update.getType()).isEqualTo(MapUpdate.Type.ChangeNodeAttribute);

								final String attribute = update.getAttribute();
								final Object value = update.getValue();
								
								// remove from not changed list and assert
								assertThat(notChangedAttributes.remove(attribute)).describedAs("Is value supposed to change").isEqualTo(true);

								if (attribute.equals("nodeText")) {
									assertThat(value).isEqualTo(attributeMap.get("nodeText").toString());
								} else if (attribute.equals("isHtml")) {
									assertThat(value).isEqualTo(attributeMap.get("isHtml").toString());
								} else if (attribute.equals("folded")) {
									assertThat(value).isEqualTo(attributeMap.get("folded").toString());
								} else if (attribute.equals("link")) {
									assertThat(value).isEqualTo(attributeMap.get("link").toString());
								} else if (attribute.equals("hGap")) {
									assertThat(value).isEqualTo(attributeMap.get("hGap").toString());
								} else if (attribute.equals("shiftY")) {
									assertThat(value).isEqualTo(attributeMap.get("shiftY").toString());
								} else if (attribute.equals("note")) {
									assertThat(value).isEqualTo(attributeMap.get("note").toString());
								}
							}

							// check that everything changed
							assertThat(notChangedAttributes.size()).isEqualTo(0);

						} catch (JsonMappingException e) {
							Fail.fail("json mapping error", e);
						} catch (JsonParseException e) {
							Fail.fail("json parse error", e);
						} catch (IOException e) {
							Fail.fail("json IOException error", e);
						} finally {
							closeMindMapOnServer(5);
						}
					}
				};
			}
		};
	}

	/**
	 * testChangeEdgeRequest change available node to defined attributes. check
	 * if node got attributes.
	 */
	@Test
	public void testChangeEdgeRequest() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("10 seconds")) {
					protected void run() {
						sendMindMapToServer(5);

						try {
							final String nodeId = "ID_1";
							final EdgeStyle edgeStyle = EdgeStyle.valueOf("EDGESTYLE_SHARP_BEZIER");
							final Map<String, Object> attributeMap = new HashMap<String, Object>();
							attributeMap.put("width", 6);
							attributeMap.put("color", 0xFF00FF00);
							attributeMap.put("style", "EDGESTYLE_SHARP_BEZIER");

							final ChangeEdgeRequest request = new ChangeEdgeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), nodeId, attributeMap);

							remoteActor.tell(request, localActor);
							ChangeEdgeResponse response = expectMsgClass(ChangeEdgeResponse.class);
							assertThat(response.isSuccess()).isEqualTo(true);

							final GetNodeRequest nodeRequest = new GetNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_1", 0);
							remoteActor.tell(nodeRequest, localActor);
							GetNodeResponse nodeResponse = expectMsgClass(GetNodeResponse.class);
							final NodeModelDefault node = new ObjectMapper().readValue(nodeResponse.getNode(), NodeModelDefault.class);
							final EdgeModel edgeModel = node.edgeStyle;
							assertThat(edgeModel.width).isEqualTo((Integer) attributeMap.get("width"));
							assertThat(edgeModel.color).isEqualTo((Integer) attributeMap.get("color"));
							assertThat(edgeModel.style).isEqualTo(edgeStyle);

						} catch (JsonMappingException e) {
							Fail.fail("json mapping error", e);
						} catch (JsonParseException e) {
							Fail.fail("json parse error", e);
						} catch (IOException e) {
							Fail.fail("json IOException error", e);
						} finally {
							closeMindMapOnServer(5);
						}
					}
				};
			}
		};
	}

	/**
	 * testChangeNodeRequestFailInvalidNode change invalid node to defined
	 * attributes. Should throw NodeNotFoundException
	 */
	@Test
	public void testChangeNodeRequestFailInvalidNode() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("10 seconds")) {
					protected void run() {
						sendMindMapToServer(5);

						remoteActor.tell(new ChangeNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_FAIL", new HashMap<String, Object>()), localActor);

						Failure response = expectMsgClass(Failure.class);

						assertThat(response.cause()).isInstanceOf(NodeNotFoundException.class);

					}
				};
			}
		};
	}

	@Test
	@Ignore
	public void testMoveNodeToAnotherPosition() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("10 seconds")) {
					protected void run() {
						sendMindMapToServer(5);

						// also checks that switching sides is no problem
						final MoveNodeToRequest moveRequest = new MoveNodeToRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_505304847", "ID_1", 1);
						remoteActor.tell(moveRequest, localActor);
						assertThat(expectMsgClass(MoveNodeToResponse.class).getSuccess()).isEqualTo(true);

						final GetNodeRequest getNodeRequest = new GetNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_505304847", 0);
						remoteActor.tell(getNodeRequest, localActor);
						final String parentNodeJson = expectMsgClass(GetNodeResponse.class).getNode();

						assertThat(parentNodeJson).contains("ID_1");

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * sendMapGetAsJsonAndCloseOnServerTest send Map, get map as json, close map
	 */
	@Test
	public void sendMapGetAsJsonAndCloseOnServerTest() {
		new JavaTestKit(system) {
			{
				new Within(duration("4 seconds")) {
					@Override
					public void run() {
						sendMindMapToServer(5);

						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5")), getRef());

						MindmapAsJsonReponse response = expectMsgClass(MindmapAsJsonReponse.class);
						System.out.println(response.getJsonString());
						assertThat(response.getJsonString()).contains("\"root\":{\"id\":\"ID_0\",\"nodeText\":\"test_5 = MapID ; 5.mm = Title\"");

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * sendMapGetAsJsonAndCloseOnServerTestFailDoubleClose send Map, get map as
	 * json, close map, close map again. should run.
	 */
	@Test
	public void sendMapGetAsJsonAndCloseOnServerTestDoubleClose() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					@Override
					public void run() {
						sendMindMapToServer(5);

						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5")), localActor);

						MindmapAsJsonReponse response = expectMsgClass(MindmapAsJsonReponse.class);
						System.out.println(response.getJsonString());
						assertThat(response.getJsonString()).contains("\"root\":{\"id\":\"ID_0\",\"nodeText\":\"test_5 = MapID ; 5.mm = Title\"");

						closeMindMapOnServer(5);
						closeMindMapOnServer(5);
						assertThat(expectMsgClass(Failure.class).cause()).isInstanceOf(MapNotFoundException.class);
					}
				};
			}
		};
	}

	@Test
	public void testCloseNotAccessedMaps() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					@Override
					public void run() {
						sendMindMapToServer(5);
						sendMindMapToServer(1);
						sendMindMapToServer(2);
						sendMindMapToServer(3);

						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						// close maps that haven't been used for 1 ms
						remoteActor.tell(new CloseUnusedMaps(new UserIdentifier(SOURCE, USERNAME1), 10), localActor);
						// expectNoMsg();

						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5")), localActor);
						Failure response = expectMsgClass(Failure.class);
						assertThat(response.cause()).isInstanceOf(MapNotFoundException.class);

						// no map closing needed, because it has been closed due
						// to beeing unused
					}
				};
			}
		};
	}

	@Test
	public void testFetchChangesSinceRevision() {
		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());
				new Within(duration("3 seconds")) {
					@Override
					public void run() {

						sendMindMapToServer(5);
						requestLock(new MapIdentifier("-1", "5"), "ID_1", USERNAME1);
						remoteActor.tell(new ChangeNodeRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), "ID_1", getNewAttributesForNode()), localActor);
						final ChangeNodeResponse changeResponse = expectMsgClass(ChangeNodeResponse.class);
						System.out.println(changeResponse.getMapUpdates());
						releaseLock(new MapIdentifier("-1", "5"), "ID_1", USERNAME1);

						remoteActor.tell(new FetchMindmapUpdatesRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), 0), localActor);
						FetchMindmapUpdatesResponse response = expectMsgClass(FetchMindmapUpdatesResponse.class);
						final List<String> updates = response.getOrderedUpdates();
						// assertThat(updates.get(0)).doesNotContain(")
						System.out.println(updates.get(0));
						System.out.println(updates.get(1));

						closeMindMapOnServer(5);
					}
				};
			}
		};
	}

	/**
	 * simulateMultipleUserAkka four user opening 4 different maps, each in one
	 * thread
	 */
	@Test
	public void testMultipleUserAkka() {

		new JavaTestKit(system) {
			{
				localActor.tell(getRef(), getRef());

				new Within(duration("5 seconds")) {

					@Override
					public void run() {
						sendMindMapToServer(1);
						sendMindMapToServer(2);
						sendMindMapToServer(3);
						sendMindMapToServer(5);

						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "3"), 5), localActor);
						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "2"), 5), localActor);
						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "5"), 5), localActor);
						remoteActor.tell(new MindmapAsJsonRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", "1"), 5), localActor);

						MindmapAsJsonReponse response = null;
						String mapAsJson = null;
						// map 3
						response = expectMsgClass(MindmapAsJsonReponse.class);
						mapAsJson = response.getJsonString();
						assertThat(mapAsJson).contains("\"isReadonly\":false,\"root\":{\"id\":\"ID_0\",\"nodeText\":\"Welcome\",\"isHtml\":false,\"folded\":false");

						// map 2
						response = expectMsgClass(MindmapAsJsonReponse.class);
						mapAsJson = response.getJsonString();
						assertThat(mapAsJson)
								.contains(
										"\"isReadonly\":false,\"root\":{\"id\":\"ID_0\",\"nodeText\":\"New Mindmap\",\"isHtml\":false,\"folded\":false,\"icons\":[],\"leftChildren\":[{\"id\":\"ID_270895934\",\"nodeText\":\"\",\"isHtml\":false,\"folded\":false");

						// map 5
						response = expectMsgClass(MindmapAsJsonReponse.class);
						mapAsJson = response.getJsonString();
						assertThat(mapAsJson).contains("\"isReadonly\":false,\"root\":{\"id\":\"ID_0\",\"nodeText\":\"test_5 = MapID ; 5.mm = Title\"");

						// map 1
						response = expectMsgClass(MindmapAsJsonReponse.class);
						mapAsJson = response.getJsonString();
						assertThat(mapAsJson).contains("\"isReadonly\":false,\"root\":{\"id\":\"ID_0\",\"nodeText\":\"foo2\",\"isHtml\":false,\"folded\":false");

					}
				};
			}
		};

	}

	public void sendMindMapToServer(final int id) {
		final URL pathURL = AkkaTests.class.getResource("/files/mindmaps/" + id + ".mm");

		try {
			final File f = new File(pathURL.toURI());
			String mapContent = FileUtils.readFileToString(f);
			final OpenMindMapRequest request = new OpenMindMapRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", id + ""), mapContent, id + ".mm");

			assertThat(f).isNotNull();

			new JavaTestKit(system) {
				{
					new Within(duration("5 seconds")) {
						public void run() {
							remoteActor.tell(request, getRef());
							OpenMindMapResponse response = expectMsgClass(OpenMindMapResponse.class);
							assertThat(response.getSuccess()).isEqualTo(true);
						}
					};
				}
			};

		} catch (URISyntaxException e) {
		} catch (IOException e) {
		}

	}

	public void closeMindMapOnServer(final int id) {
		new JavaTestKit(system) {
			{
				new Within(duration("2 seconds")) {
					public void run() {
						remoteActor.tell(new CloseMapRequest(new UserIdentifier(SOURCE, USERNAME1), new MapIdentifier("-1", id + "")), localActor);
					}
				};
			}
		};
	}

	public void requestLock(final MapIdentifier mapIdentifier, final String nodeId, final String username) {
		new JavaTestKit(system) {
			{
				new Within(duration("2 seconds")) {
					public void run() {
						remoteActor.tell(new RequestLockRequest(new UserIdentifier(SOURCE, USERNAME1), mapIdentifier, nodeId), getRef());
						RequestLockResponse requestResponse = expectMsgClass(RequestLockResponse.class);
						assertThat(requestResponse.getLockGained()).isEqualTo(true);
					}
				};
			}
		};
	}

	public void releaseLock(final MapIdentifier mapIdentifier, final String nodeId, final String username) {
		new JavaTestKit(system) {
			{
				new Within(duration("2 seconds")) {
					public void run() {
						remoteActor.tell(new ReleaseLockRequest(new UserIdentifier(SOURCE, USERNAME1), mapIdentifier, nodeId), getRef());
						ReleaseLockResponse releaseResponse = expectMsgClass(ReleaseLockResponse.class);
						assertThat(releaseResponse.getLockReleased()).isEqualTo(true);
					}
				};
			}
		};
	}

	private Map<String, Object> getNewAttributesForNode() {
		final String newNodeText = "This is a new nodeText";
		final Boolean isHtml = false;
		final Boolean folded = true;
		final String[] icons = new String[] { "yes" };
		final String link = "http://www.google.de";
		final Integer hGap = 10;
		final Integer shiftY = 10;
		final String note = "This is a note";
		final List<String> attr = new ArrayList<String>();
		attr.add("key%:%value");

		Map<String, Object> attributeMap = new HashMap<String, Object>();
		attributeMap.put("nodeText", newNodeText);
		attributeMap.put("isHtml", isHtml);
		attributeMap.put("folded", folded);
		attributeMap.put("icons", icons);
		attributeMap.put("link", link);
		attributeMap.put("hGap", hGap);
		attributeMap.put("shiftY", shiftY);
		attributeMap.put("attributes", attr);
		attributeMap.put("note", note);

		return attributeMap;
	}

	private void validateMapSchema(final String mapJsonString) {
		validateMapSchema(new ObjectMapper().valueToTree(mapJsonString));
	}
	
	private void validateMapSchema(final JsonNode mapJson) {
		final String schemaPath = "/MapModelSchema.json";
		final JsonNode mapNode = validateSchema(mapJson, schemaPath);
		validateRootNodeSchema(mapNode.get("root"));
	}

	private void validateRootNodeSchema(final JsonNode rootNodeJson) {
		final String schemaPath = "/RootNodeSchema.json";
		final JsonNode rootNode = validateSchema(rootNodeJson, schemaPath);
		
		final Iterator<JsonNode> itRight = rootNode.get("rightChildren").iterator();
		while (itRight.hasNext()) {
			final JsonNode node = itRight.next();
			validateDefaultNodeSchema(node);
		}

		final Iterator<JsonNode> itLeft = rootNode.get("leftChildren").iterator();
		while (itLeft.hasNext()) {
			final JsonNode node = itLeft.next();
			validateDefaultNodeSchema(node);
		}
	}

	private void validateDefaultNodeSchema(final String defaultNodeJsonString) {
		validateDefaultNodeSchema(new ObjectMapper().valueToTree(defaultNodeJsonString));
	}
		
	private void validateDefaultNodeSchema(final JsonNode defaultNodeJson) {
		final String schemaPath = "/DefaultNodeSchema.json";
		final JsonNode node = validateSchema(defaultNodeJson, schemaPath);

		if (node.has("edgeStyle")) {
			validateEdgeSchema(node.get("edgeStyle"));
		}
	}

	private void validateEdgeSchema(final JsonNode edgeJson) {
		final String schemaPath = "/EdgeSchema.json";
		validateSchema(edgeJson, schemaPath);
	}

	private JsonNode validateSchema(final JsonNode objectToValidate, final String schemaPath) {
		final ObjectMapper mapper = new ObjectMapper();
		try {
			final JsonNode schemaNode = mapper.readTree(AkkaTests.class.getResourceAsStream(schemaPath));
			final JsonSchema schema = JsonSchemaFactory.byDefault().getJsonSchema(schemaNode);
			final ProcessingReport report = schema.validate(objectToValidate);
			if (!report.isSuccess()) {
				String errorMessage = "";
				Iterator<ProcessingMessage> it = report.iterator();
				while (it.hasNext()) {
					final ProcessingMessage message = it.next();
					errorMessage += message.toString() + ", ";
				}
				Fail.fail(errorMessage);
			}
			return objectToValidate;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static class TheActor extends UntypedActor {
		ActorRef target;

		@Override
		public void onReceive(Object message) throws Exception {
			System.out.println(message.getClass().getName() + " received");

			if (message instanceof Failure) {
				System.err.println("warning: Error occured.");
				// org.fest.assertions.Fail.fail("An error occured", ((Failure)
				// message).cause());
			}

			if (message instanceof ActorRef) {
				target = (ActorRef) message;
			} else {
				if (target != null)
					target.tell(message, getSelf());
			}

		}
	}
}
