package ca.uhn.fhir.cli;

import ca.uhn.fhir.model.primitive.IdDt;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class WebsocketSubscribeCommand extends BaseCommand {
	private static final org.slf4j.Logger LOG_RECV = org.slf4j.LoggerFactory.getLogger("websocket.RECV");
	private static final org.slf4j.Logger LOG_SEND = org.slf4j.LoggerFactory.getLogger("websocket.SEND");
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(WebsocketSubscribeCommand.class);

	private boolean myQuit;

	@Override
	public String getCommandDescription() {
		return "Opens a websocket client connection to a server for the purposes of binding to a Subscription, and then outputs the results";
	}

	@Override
	public String getCommandName() {
		return "subscription-client";
	}

	@Override
	public Options getOptions() {
		Options options = new Options();
		options.addOption("t", "target", true, "Target URL, e.g. \"ws://fhirtest.uhn.ca/websocket/dstu2\"");
		options.addOption("i", "subscriptionid", true, "Subscription ID, e.g. \"5235\"");
		return options;
	}

	@Override
	public void run(CommandLine theCommandLine) throws ParseException {
		String target = theCommandLine.getOptionValue("t");
		if (isBlank(target) || (!target.startsWith("ws://") && !target.startsWith("wss://"))) {
			throw new ParseException("Target (-t) needs to be in the form \"ws://foo\" or \"wss://foo\"");
		}

		IdDt subsId = new IdDt(theCommandLine.getOptionValue("i"));

		WebSocketClient client = new WebSocketClient();
		SimpleEchoSocket socket = new SimpleEchoSocket(subsId.getIdPart());
		try {
			client.start();
			URI echoUri = new URI(target);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			ourLog.info("Connecting to : {}", echoUri);
			client.connect(socket, echoUri, request);

			while (!myQuit) {
				Thread.sleep(500L);
			}

			ourLog.info("Shutting down websocket client");
		} catch (Exception e) {
			throw new CommandFailureException(e);
		} finally {
			try {
				client.stop();
			} catch (Exception e) {
				ourLog.error("Failure", e);
			}
		}
	}

	/**
	 * Basic Echo Client Socket
	 */
	@WebSocket(maxTextMessageSize = 64 * 1024)
	public class SimpleEchoSocket {

		private String mySubsId;

		@SuppressWarnings("unused")
		private Session session;


		public SimpleEchoSocket(String theSubsId) {
			mySubsId = theSubsId;
		}

		@OnWebSocketClose
		public void onClose(int statusCode, String reason) {
			ourLog.info("Received CLOSE status={} reason={}", statusCode, reason);
		}

		@OnWebSocketConnect
		public void onConnect(Session theSession) {
			ourLog.info("Successfully connected");
			this.session = theSession;
			try {
				String sending = "bind " + mySubsId;
				LOG_SEND.info("{}", sending);
				theSession.getRemote().sendString(sending);
			} catch (Throwable t) {
				ourLog.error("Failure", t);
				myQuit = true;
			}
		}

		@OnWebSocketError
		public void onError(Throwable theError) {
			ourLog.error("Websocket error: ", theError);
			myQuit = true;
		}

		@OnWebSocketFrame
		public void onFrame(Frame theFrame) {
			ourLog.debug("Websocket frame: {}", theFrame);
		}

		@OnWebSocketMessage
		public void onMessage(String theMsg) {
			LOG_RECV.info("{}", theMsg);
		}
	}

}