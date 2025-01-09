import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class MP extends MIDlet implements CommandListener, Runnable {

	private static final int RUN_AUTH = 1;
	private static final int RUN_DIALOGS = 2;
	private static final int RUN_CHAT = 3;
	private static final int RUN_SEND = 4;
	private static final int RUN_BACKGROUND = 5;
	
	private static MP midlet;
	private static Display display;

	// commands
	private static Command authCmd;
	private static Command exitCmd;
	private static Command backCmd;
	private static Command writeCmd;
	private static Command sendCmd;
	private static Command updateCmd;
	
	// ui
	private static Form authForm;
	private static List dialogsList;
	private static Form chatForm;
	private static TextBox writeBox;
	private static Form initForm;

	// ui elements
	private static TextField tokenField;

	// threading
	private static boolean running;
	private static int run;
	
	// settings
	private static String user;
	private static String instance = "http://mp2.nnchan.ru/";
	private static int tzOffset;
	private static boolean showMedia;
	
	private static String version;
	
	private static JSONArray dialogs;
	
	private static JSONObject usersCache;
	private static JSONObject chatsCache;
	
	private static String currentChatPeer;

	protected void destroyApp(boolean u) {
	}

	protected void pauseApp() {
	}

	protected void startApp()  {
		if (midlet != null) return;
		midlet = this;
		display = Display.getDisplay(this);
		
		version = getAppProperty("MIDlet-Version");
		
		exitCmd = new Command("Exit", Command.EXIT, 10);
		backCmd = new Command("Back", Command.BACK, 10);
		authCmd = new Command("Auth", Command.OK, 1);
		writeCmd = new Command("Write", Command.SCREEN, 2);
		sendCmd = new Command("Send", Command.OK, 1);
		updateCmd = new Command("Update", Command.SCREEN, 3);
		
		initForm = new Form("mpgram");
		initForm.append("Loading");
		display(initForm);
		
		try {
			RecordStore r = RecordStore.openRecordStore("mpgramuser", false);
			user = new String(r.getRecord(1));
			r.closeRecordStore();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			tzOffset = TimeZone.getDefault().getRawOffset() / 1000;
		} catch (Throwable e) {} // just to be sure
		
		if (user == null) {
			display(authForm());
		} else {
			start(RUN_AUTH);
		}
		
		start(RUN_BACKGROUND);
	}
	
	public void run() {
		int run;
		synchronized (this) {
			run = MP.run;
			notify();
		}
		running = true;
		switch (run) {
		case RUN_AUTH: {
			try {
				if (tokenField != null) user = tokenField.getString();
				api("checkAuth");
				
				usersCache = new JSONObject();
				chatsCache = new JSONObject();
				
				dialogsList = new List("Dialogs", List.IMPLICIT);
				dialogsList.addCommand(exitCmd);
				dialogsList.addCommand(List.SELECT_COMMAND);
				dialogsList.setCommandListener(this);
				dialogsList.setFitPolicy(List.TEXT_WRAP_ON);
				
				display(dialogsList);
				
				try {
					RecordStore.deleteRecordStore("mpgramuser");
				} catch (Exception ignored) {}
				try {
					RecordStore r = RecordStore.openRecordStore("mpgramuser", true);
					byte[] b = user.getBytes();
					r.addRecord(b, 0, b.length);
					r.closeRecordStore();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), authForm());
				break;
			}
		}
		case RUN_DIALOGS: {
			try {
				List list = dialogsList;
				list.deleteAll();
				JSONObject j = api("getDialogs&limit=15&fields=dialogs,users,chats");
				dialogs = j.getArray("dialogs");
				
				JSONObject chats = j.getNullableObject("chats");
				JSONObject users = j.getNullableObject("users");
				fillPeersCache(users, chats);
				
				JSONObject msgs = j.getNullableObject("messages");
				
				for (int i = 0, l = dialogs.size(); i < l; ++i) {
					JSONObject dialog = dialogs.getObject(i);
					String id = dialog.getString("id");
					
					String title = "";
					String m = "";
					
					JSONObject p;
					if (id.charAt(0) == '-') {
						p = chats.getNullableObject(id);
						if (p != null) title = p.getString("title");
					} else {
						p = users.getNullableObject(id);
						if (p != null) title = getName(p);
					}

					JSONObject msg = msgs.getObject(id);
					if (msg != null) {
						m = oneLine(msg.getString("text", ""));
					}
					
					dialogsList.append(title.concat("\n").concat(m), null);
				}
				
				if (dialogsList == list) {
					display(list);
				}
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), dialogsList);
			}
			break;
		}
		case RUN_CHAT: {
			try {
				String title = getName(currentChatPeer, false, false);
				Form f = chatForm = new Form(title == null ? "Chat" : title);
				f.addCommand(backCmd);
				f.addCommand(writeCmd);
				f.addCommand(updateCmd);
				f.setCommandListener(this);
				
				if (writeBox != null) {
					writeBox.setString("");
				}
				StringBuffer sb = new StringBuffer();
				sb.append("getHistory&peer=").append(currentChatPeer);
				if (showMedia) sb.append("&include_media");
				
				JSONObject j = api(sb.toString());
				
				JSONObject chats = j.getNullableObject("chats");
				JSONObject users = j.getNullableObject("users");
				fillPeersCache(users, chats);
				
				JSONArray msgs = j.getArray("messages");
				
				title = getShortName(currentChatPeer);
				
				long time, lastTime = 0;
				Calendar c = Calendar.getInstance();

				String label;
				String type;
				for (int i = 0, l = msgs.size(); i < l; ++i) {
					JSONObject msg = msgs.getObject(i);
					time = msg.getLong("date") + tzOffset;
					if (time == 0 || (time / 86400 != lastTime / 86400)) {
						c.setTime(new Date((time - tzOffset) * 1000L));
						sb.setLength(0);
						sb.append(c.get(Calendar.DAY_OF_MONTH));
						if (sb.length() < 2) sb.insert(0, '0');
						
						sb.append('.')
						.append(c.get(Calendar.MONTH) + 1);
						if (sb.length() < 5) sb.insert(3, '0');
						
						sb.append('.')
						.append(c.get(Calendar.YEAR));
						f.append(new StringItem(null, sb.toString()));
					}
					lastTime = time;
					
					sb.setLength(0);
					
					sb.append(' ')
					.append((time / 3600) % 24);
					if (sb.length() < 3) sb.insert(1, '0');
					
					sb.append(':')
					.append((time / 60) % 60);
					if (sb.length() < 6) sb.insert(4, '0');
					
					label = sb.insert(0, msg.has("from_id") ? getName(msg.getString("from_id")) : title).toString();
					
					sb.setLength(0);
					if (msg.has("fwd")) sb.append("(Forwarded) ");
					if (msg.has("reply")) sb.append("(Reply)\n");
					if (msg.has("action")) {
						sb.append("(Action)");
					} else {
						sb.append(msg.getString("text", ""));
						if (sb.length() != 0) sb.append('\n');
						
						JSONObject media;
						if (msg.has("media")) {
							if (showMedia
									&& (media = msg.getNullableObject("media")) != null
									&& (type = media.getNullableString("type")) != null) {
								if ("photo".equals(type)) {
									sb.append("(Photo)");
								} else if ("document".equals(type)) {
									if (media.has("audio")) {
										JSONObject audio = media.getObject("audio");
										boolean voice;
										sb.append((voice = audio.getBoolean("voice", false)) ?
												"(Voice: " : "(Audio: ");
										
										if (voice) {
											int t = audio.getInt("time", 0);
											
											sb.append(t / 60);
											if (sb.length() < 10) sb.insert(8, '0');
											
											sb.append(':').append(t % 60);
											if (sb.length() < 13) sb.insert(11, '0');
										} else {
											if (audio.has("artist")) sb.append(audio.getString("artist")).append(" - ");
											sb.append(audio.getString("title",
													media.getString("name", "Unknown")));
										}
										sb.append(")");
									} else {
										sb.append("(Document: ")
										.append(media.getString("name", "Unknown"))
										.append(")");
									}
								} else {
									sb.append("(Media)");
								}
							} else sb.append("(Media)");
						}
					}
					f.append(new StringItem(label, sb.append('\n').toString()));
				}
				if (f == chatForm) display(chatForm);
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), chatForm);
			}
			break;
		}
		case RUN_SEND: {
			try {
				String s = writeBox.getString();
				writeBox.setString("");
				
				api("sendMessage&peer=".concat(currentChatPeer).concat("&text=").concat(url(s)));
				
				display(chatForm);
				MP.run = RUN_CHAT;
				run();
				return;
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), chatForm);
			}
			break;
		}
		case RUN_BACKGROUND: {
			// TODO
			break;
		}
		}
		running = false;
	}

	Thread start(int i) {
		Thread t = null;
		try {
			synchronized(this) {
				run = i;
				(t = new Thread(this)).start();
				wait();
			}
		} catch (Exception e) {}
		return t;
	}
	
	private void addBackgroundTask(Object a) {
		// TODO
	}

	public void commandAction(Command c, Displayable d) {
		if (d == chatForm) {
			if (c == writeCmd) {
				if (writeBox == null) {
					writeBox = new TextBox("Write", "", 500, TextField.ANY);
					writeBox.addCommand(backCmd);
					writeBox.addCommand(sendCmd);
					writeBox.setCommandListener(this);
				}
				display(writeBox);
				return;
			}
		}
		if (d == writeBox) {
			if (c == sendCmd) {
				display(loadingAlert(), chatForm);
				start(RUN_SEND);
				return;
			}
			if (c == backCmd) {
				display(chatForm, true);
				return;
			}
		}
		if (d == dialogsList) {
			if (c == List.SELECT_COMMAND) {
				int i = ((List) d).getSelectedIndex();
				if (i == -1 || running) return;
				String title = getName(currentChatPeer = dialogs.getObject(i).getString("id"), false, false);
				chatForm = new Form(title == null ? "Chat" : title);
				chatForm.addCommand(backCmd);
				chatForm.addCommand(writeCmd);
				chatForm.setCommandListener(this);
				display(loadingAlert(), dialogsList);
				
				start(RUN_CHAT);
				return;
			}
		}
		if (d == authForm) {
			if (c == authCmd) {
				start(RUN_AUTH);
				return;
			}
		}
		if (c == backCmd) {
			display(null);
			return;
		}
		if (c == exitCmd) {
			notifyDestroyed();
		}
	}

	private Form authForm() {
		if (authForm != null) return authForm;
		
		authForm = new Form("Auth");
		authForm.addCommand(authCmd);
		authForm.addCommand(exitCmd);
		authForm.setCommandListener(this);
		
		tokenField = new TextField("User session", "", 200, TextField.ANY);
		authForm.append(tokenField);
		
		return authForm;
	}

	private static void fillPeersCache(JSONObject users, JSONObject chats) {
		if (users != null && usersCache != null) {
			if (usersCache.size() > 200) {
				usersCache.clear();
			}
			for (Enumeration e = users.keys(); e.hasMoreElements(); ) {
				String k = (String) e.nextElement();
				if ("0".equals(k)) continue;
				usersCache.put(k, (JSONObject) users.get(k));
			}
		}
		if (chats != null && chatsCache != null) {
			if (chatsCache.size() > 200) {
				chatsCache.clear();
			}
			for (Enumeration e = chats.keys(); e.hasMoreElements(); ) {
				String k = (String) e.nextElement();
				if ("0".equals(k)) continue;
				chatsCache.put(k, (JSONObject) chats.get(k));
			}
		}
	}

	// utils
	
	private static String oneLine(String s) {
		if (s == null) return null;
		StringBuffer sb = new StringBuffer();
		int i = 0, l = s.length();
		while (i < l && i < 64) {
			char c = s.charAt(i++);
			if (c == '\r') continue;
			if (c != '\n') sb.append(c);
			else sb.append(' ');
		}
		return sb.toString();
	}
	
	private static String getName(String id) {
		return getName(id, false, true);
	}
	
	private static String getShortName(String id) {
		return getName(id, true, true);
	}
	
	private static String getName(String id, boolean variant, boolean loadIfNeeded) {
		String res;
		if (id.charAt(0) == '-') {
			res = chatsCache.getObject(id).getString("title");
		} else {
			JSONObject o = usersCache.getObject(id);
			res = variant ? getShortName(o) : getName(o);
		}
		if (res == null) {
			if (!loadIfNeeded) return null;
			// TODO put to load queue
			throw new RuntimeException("Not implemented");
		}
		return res;
	}
	
	private static String getNameLater(String id, Object target, boolean variant) {
		String r = getName(id, variant, false);
		if (r != null) {
			return r;
		}
		// TODO
		return null;
	}
	
	private static String getName(JSONObject p) {
		if (p == null) return null;
		if (p.has("title")) {
			return p.getString("title");
		}
		
		String fn = p.getString("first_name");
		String ln = p.getString("last_name");
		
		if (fn != null && ln != null) {
			return fn.concat(" ").concat(ln);
		}
		
		if (ln != null) {
			return ln;
		}
		
		if (fn != null) {
			return fn;
		}
		
		return "Deleted";
	}
	
	private static String getShortName(JSONObject p) {
		if (p.has("title")) {
			return p.getString("title");
		}
		
		String fn = p.getString("first_name");
		String ln = p.getString("last_name");
		
		if (fn != null) {
			return fn;
		}
		
		if (ln != null) {
			return ln;
		}
		
		return "Deleted";
	}
	
	static void display(Alert a, Displayable d) {
		if (d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}
	
	static void display(Displayable d) {
		display(d, false);
	}

	static void display(Displayable d, boolean back) {
		if (d instanceof Alert) {
			display.setCurrent((Alert) d, dialogsList != null ? (Displayable) dialogsList : authForm != null ? authForm : initForm);
			return;
		}
		if (d == null)
			d = dialogsList != null ? (Displayable) dialogsList : authForm != null ? authForm : initForm;
		display.setCurrent(d);
	}

	private static Alert errorAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.ERROR);
		a.setString(text);
		a.setTimeout(3000);
		return a;
	}
	
	private static Alert infoAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.CONFIRMATION);
		a.setString(text);
		a.setTimeout(1500);
		return a;
	}
	
	private static Alert loadingAlert() {
		Alert a = new Alert("", "Loading", null, null);
		a.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
		a.setTimeout(30000);
		return a;
	}
	
	private static JSONObject api(String url) throws IOException {
		JSONObject res;

		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(instance.concat("api.php?v=4&method=").concat(url));
			hc.setRequestMethod("GET");
			int c;
			if ((c = hc.getResponseCode()) >= 400 && c != 500) {
				throw new IOException("HTTP ".concat(Integer.toString(c)));
			}
			res = JSONObject.parseObject(readUtf(in = hc.openInputStream(), (int) hc.getLength()));
		} finally {
			if (in != null) try {
				in.close();
			} catch (IOException e) {}
			if (hc != null) try {
				hc.close();
			} catch (IOException e) {}
		}
		System.out.println(res);
		// хендлить ошибки апи
		if (res.has("error")) {
			throw new RuntimeException("API error: ".concat(res.getString("error")).concat("\nURL: ").concat(url));
		}
		return res;
	}

	private static Image getImage(String url) throws IOException {
		byte[] b = get(url);
		return Image.createImage(b, 0, b.length);
	}
	
	private static byte[] readBytes(InputStream inputStream, int initialSize, int bufferSize, int expandSize)
			throws IOException {
		if (initialSize <= 0) initialSize = bufferSize;
		byte[] buf = new byte[initialSize];
		int count = 0;
		byte[] readBuf = new byte[bufferSize];
		int readLen;
		while ((readLen = inputStream.read(readBuf)) != -1) {
			if (count + readLen > buf.length) {
				System.arraycopy(buf, 0, buf = new byte[count + expandSize], 0, count);
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if (buf.length == count) {
			return buf;
		}
		byte[] res = new byte[count];
		System.arraycopy(buf, 0, res, 0, count);
		return res;
	}
	
	private static String readUtf(InputStream in, int i) throws IOException {
		byte[] buf = new byte[i <= 0 ? 1024 : i];
		i = 0;
		int j;
		while ((j = in.read(buf, i, buf.length - i)) != -1) {
			if ((i += j) >= buf.length) {
				System.arraycopy(buf, 0, buf = new byte[i + 2048], 0, i);
			}
		}
		return new String(buf, 0, i, "UTF-8");
	}
	
	private static byte[] get(String url) throws IOException {
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(url);
			hc.setRequestMethod("GET");
			int r;
			if ((r = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP ".concat(Integer.toString(r)));
			}
			in = hc.openInputStream();
			return readBytes(in, (int) hc.getLength(), 8*1024, 16*1024);
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {}
			try {
				if (hc != null) hc.close();
			} catch (IOException e) {}
		}
	}
	
	private static HttpConnection open(String url) throws IOException {
		HttpConnection hc = (HttpConnection) Connector.open(url);
		hc.setRequestProperty("User-Agent", "mpgram3/".concat(version));
		if (user != null) {
			hc.setRequestProperty("X-mpgram-user", user);
		}
		return hc;
	}
	
	public static String url(String url) {
		StringBuffer sb = new StringBuffer();
		char[] chars = url.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int c = chars[i];
			if (65 <= c && c <= 90) {
				sb.append((char) c);
			} else if (97 <= c && c <= 122) {
				sb.append((char) c);
			} else if (48 <= c && c <= 57) {
				sb.append((char) c);
			} else if (c == 32) {
				sb.append("%20");
			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
					|| c == 41) {
				sb.append((char) c);
			} else if (c <= 127) {
				sb.append(hex(c));
			} else if (c <= 2047) {
				sb.append(hex(0xC0 | c >> 6));
				sb.append(hex(0x80 | c & 0x3F));
			} else {
				sb.append(hex(0xE0 | c >> 12));
				sb.append(hex(0x80 | c >> 6 & 0x3F));
				sb.append(hex(0x80 | c & 0x3F));
			}
		}
		return sb.toString();
	}

	private static String hex(int i) {
		String s = Integer.toHexString(i);
		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
	}

}
