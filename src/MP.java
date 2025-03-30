import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import cc.nnproject.json.JSONStream;

public class MP extends MIDlet implements CommandListener, ItemCommandListener, Runnable {

	static final int RUN_SEND_MESSAGE = 4;
	static final int RUN_VALIDATE_AUTH = 5;
	static final int RUN_AVATARS = 6;
	static final int RUN_UPDATES = 7;
	static final int RUN_LOAD_FORM = 8;
	static final int RUN_LOAD_LIST = 9;
	
	private static final String SETTINGS_RECORDNAME = "mp4config";
	private static final String AUTH_RECORDNAME = "mp4user";
	
	private static final String DEFAULT_INSTANCE_URL = "http://mp2.nnchan.ru/";
	private static final String API_URL = "api.php";
	private static final String AVA_URL = "ava.php";
	private static final String FILE_URL = "file.php";
	
	private static final String API_VERSION = "5";
	
	static final Font largePlainFont = Font.getFont(0, 0, Font.SIZE_LARGE);
	static final Font medPlainFont = Font.getFont(0, 0, Font.SIZE_MEDIUM);
	static final Font medBoldFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
	static final Font medItalicFont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	static final Font medItalicBoldFont = Font.getFont(0, Font.STYLE_BOLD | Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	static final Font smallPlainFont = Font.getFont(0, 0, Font.SIZE_SMALL);
	static final Font smallBoldFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_SMALL);
	static final Font smallItalicFont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_SMALL);

	static final IllegalStateException cancelException = new IllegalStateException("cancel");
	
	// midp lifecycle
	static MP midlet;
	static Display display;
	static Displayable current;

	private static String version;

	// localization
	static String[] L;
	
	// settings
	private static String instanceUrl = DEFAULT_INSTANCE_URL;
	private static String instancePassword;
	private static int tzOffset;
	private static boolean showMedia;
	private static boolean avatars;
	private static boolean symbianJrt;
	static boolean useLoadingForm;
	private static int avatarSize;
	static boolean loadAvatars;
	static boolean reverseChat = true;

	// threading
	private static int run;
	private static Object runParam;
//	private static int running;
	private static boolean avatarsRunning;
	private static boolean updatesRunning;
	
	private static Object avatarsLoadLock = new Object();
	private static Vector avatarsToLoad = new Vector();
	
	// auth
	private static String user;
	private static int userState;
	private static String phone;

	// commands
	private static Command exitCmd;
	static Command backCmd;

	private static Command settingsCmd;
	private static Command aboutCmd;
	
	private static Command authCmd;
	private static Command authNewSessionCmd;
	private static Command authImportSessionCmd;

	static Command refreshCmd;
	static Command archiveCmd;
	static Command foldersCmd;
	static Command contactsCmd;
	static Command searchCmd;

	static Command itemChatCmd;
	static Command itemChatInfoCmd;
	static Command replyMsgCmd;
	static Command forwardMsgCmd;
	static Command copyMsgCmd;
	static Command richTextLinkCmd;

	static Command writeCmd;
	static Command chatInfoCmd;
	static Command sendCmd;
	static Command updateCmd;

	static Command okCmd;
	static Command nextCmd;
	static Command cancelCmd;
	
	// ui
	private static Displayable mainDisplayable;
	static Form loadingForm;
	static ChatsList chatsList;
	static FoldersList foldersList;
	private static Vector formHistory = new Vector();

	// ui elements
//	private static TextField tokenField;
	private static TextField instanceField;
	private static TextField instancePasswordField;
	
//	private static JSONArray dialogs;

	private static JSONObject usersCache = new JSONObject();
	private static JSONObject chatsCache = new JSONObject();
	
	private static String richTextUrl;

	protected void destroyApp(boolean u) {
	}

	protected void pauseApp() {
	}

	protected void startApp()  {
		if (midlet != null) return;
		midlet = this;

		version = getAppProperty("MIDlet-Version");
		display = Display.getDisplay(this);
		
		String p = System.getProperty("microedition.platform");
		symbianJrt = p != null && p.indexOf("platform=S60") != -1;
		useLoadingForm = !symbianJrt &&
				(System.getProperty("com.symbian.midp.serversocket.support") != null ||
				System.getProperty("com.symbian.default.to.suite.icon") != null);
		
		// TODO refuse to run in j2me loader
		
		avatarSize = Math.min(display.getBestImageHeight(Display.LIST_ELEMENT), display.getBestImageWidth(Display.LIST_ELEMENT));
		if (avatarSize < 4) avatarSize = 16;
		else if (avatarSize > 120) avatarSize = 120;
		
		try {
			tzOffset = TimeZone.getDefault().getRawOffset() / 1000;
		} catch (Throwable e) {} // just to be sure
		
		// load settings
		try {
			RecordStore r = RecordStore.openRecordStore(SETTINGS_RECORDNAME, false);
			JSONObject j = JSONObject.parseObject(new String(r.getRecord(1), "UTF-8"));
			r.closeRecordStore();
			
			// TODO
		} catch (Exception ignored) {}
		
		// load auth
		try {
			RecordStore r = RecordStore.openRecordStore(AUTH_RECORDNAME, false);
			JSONObject j = JSONObject.parseObject(new String(r.getRecord(1), "UTF-8"));
			r.closeRecordStore();

			user = j.getString("user", user);
			userState = j.getInt("userState", 0);
		} catch (Exception ignored) {}
	
		
		// load locale TODO
//		(L = new String[200])[0] = "mpgram";
//		try {
//			loadLocale(lang);
//		} catch (Exception e) {
//			try {
//				loadLocale(lang = "en");
//			} catch (Exception e2) {
//				// crash on fail
//				throw new RuntimeException(e2.toString());
//			}
//		}
		
		// commands
		
		exitCmd = new Command("Exit", Command.EXIT, 10);
		backCmd = new Command("Back", Command.BACK, 10);
		
		settingsCmd = new Command("Settings", Command.SCREEN, 5);
		aboutCmd = new Command("About", Command.SCREEN, 6);
		
		authCmd = new Command("Auth", Command.ITEM, 1);
		authNewSessionCmd = new Command("New session", Command.SCREEN, 1);
		authImportSessionCmd = new Command("Import session", Command.SCREEN, 2);

		refreshCmd = new Command("Refresh", Command.SCREEN, 4);
		archiveCmd = new Command("Archived chats", Command.SCREEN, 5);
		foldersCmd = new Command("Folders", Command.SCREEN, 5);
		contactsCmd = new Command("Contacts", Command.SCREEN, 6);
		searchCmd = new Command("Search", Command.SCREEN, 7);
		
		itemChatCmd = new Command("Open chat", Command.ITEM, 1);
		itemChatInfoCmd = new Command("Profile", Command.ITEM, 2);
		replyMsgCmd = new Command("Reply", Command.ITEM, 3);
		forwardMsgCmd = new Command("Forward", Command.ITEM, 4);
		copyMsgCmd = new Command("Copy message", Command.ITEM, 5);
		richTextLinkCmd = new Command("Link", Command.ITEM, 1);
		
		writeCmd = new Command("Write", Command.SCREEN, 6);
		chatInfoCmd = new Command("Chat info", Command.SCREEN, 7);
		sendCmd = new Command("Send", Command.OK, 1);
		updateCmd = new Command("Update", Command.SCREEN, 3);
		
		okCmd = new Command("Ok", Command.OK, 1);
		nextCmd = new Command("Next", Command.OK, 1);
		cancelCmd = new Command("Cancel", Command.CANCEL, 2);
		
		loadingForm = new Form("mpgram");
		loadingForm.append("Loading");
		loadingForm.addCommand(cancelCmd);
		loadingForm.setCommandListener(this);
		
		Form f = new Form("mpgram");
		f.append("Loading");
		display(mainDisplayable = f);
		
		if (user == null) {
			display(mainDisplayable = initialAuthForm());
			return;
		} else {
			run = RUN_VALIDATE_AUTH;
			run();
		}

		start(RUN_AVATARS, null);

		if (loadAvatars && symbianJrt) {
			start(RUN_AVATARS, null);
		}
		
		ChatsList l = mainChatsList();
		start(RUN_LOAD_LIST, l);
		display(mainDisplayable = l);
	}
	
	public void run() {
		int run;
		Object param;
		synchronized (this) {
			run = MP.run;
			param = MP.runParam;
			notify();
		}
		System.out.println("run " + run + " " + param);
//		running++;
		switch (run) {
		case RUN_VALIDATE_AUTH: {
			display(loadingAlert("Authorizing"), null);
			
			try {
				api("me");
				
				if (param != null) {
					ChatsList l = mainChatsList();
					start(RUN_LOAD_LIST, l);
					display(mainDisplayable = l);
				}
//				if (updatesRunning) break;
//				start(RUN_UPDATES, null);
			} catch (APIException e) {
				user = null;
				display(errorAlert(e.toString()), mainDisplayable = initialAuthForm());
			} catch (IOException e) {
				display(errorAlert(e.toString()), null);
			}
			break;
		}
		case RUN_UPDATES: {
			// TODO
			if (updatesRunning) break;
			updatesRunning = true;
			try {
				while (true) {
					
					Thread.sleep(30000L);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		}
		case RUN_AVATARS: { // avatars loading
			try {
				while (true) {
					synchronized (avatarsLoadLock) {
						avatarsLoadLock.wait();
					}
					Thread.sleep(200);
					while (avatarsToLoad.size() > 0) {
						Object[] o = null;
						
						try {
							synchronized (avatarsLoadLock) {
								o = (Object[]) avatarsToLoad.elementAt(0);
								avatarsToLoad.removeElementAt(0);
							}
						} catch (Exception e) {
							continue;
						}
						
						if (o == null) continue;
						
						String id = (String) o[0];
						Object target = o[1];
						
						if (id == null) continue;
						
						try {
							Image img = getImage(instanceUrl + AVA_URL + "?a&c=" + id + "&p=r" + avatarSize);
								
							if (target instanceof ImageItem) {
								((ImageItem) target).setImage(img);
							} else if (target instanceof Object[]) {
								if (((Object[]) target)[0] instanceof List) {
									List list = ((List) ((Object[]) target)[0]);
									int idx = (((Integer) ((Object[]) target)[1])).intValue();
									list.set(idx, list.getString(idx), img);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						} 
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		case RUN_LOAD_FORM: {
			((MPForm) param).load();
			break;
		}
		case RUN_LOAD_LIST: {
			((MPList) param).load();
			break;
		}
		}
//		running--;
	}

	Thread start(int i, Object param) {
		Thread t = null;
		try {
			synchronized(this) {
				run = i;
				runParam = param;
				(t = new Thread(this)).start();
				wait();
			}
		} catch (Exception e) {}
		return t;
	}

	public void commandAction(Command c, Displayable d) {
		if (d instanceof MPList && c == List.SELECT_COMMAND) {
			((MPList) d).select(((List) d).getSelectedIndex());
			return;
		}
		if (c == foldersCmd) {
			if (foldersList == null) {
				foldersList = new FoldersList();
				start(RUN_LOAD_LIST, foldersList);
			}
			display(foldersList);
		}
		if (c == aboutCmd) {
			Form f = new Form("About");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			try {
				f.append(new ImageItem(null, Image.createImage("/g.png"), Item.LAYOUT_LEFT, null));
			} catch (Exception ignored) {}
			
			StringItem s;
			s = new StringItem(null, "MPGram v".concat(version));
			s.setFont(largePlainFont);
			s.setLayout(Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_VCENTER | Item.LAYOUT_LEFT);
			f.append(s);
			
			s = new StringItem(null, "mpgram 4th");
			s.setFont(Font.getDefaultFont());
			s.setLayout(Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
			f.append(s);

			s = new StringItem("Developer", "shinovon");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);

			s = new StringItem("Author", "twsparkle");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			s.setItemCommandListener(this);
			f.append(s);

			s = new StringItem("GitHub", "github.com/shinovon");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			s.setItemCommandListener(this);
			f.append(s);

			s = new StringItem("Web", "nnproject.cc");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			s.setItemCommandListener(this);
			f.append(s);

			s = new StringItem("Donate", "boosty.to/nnproject/donate");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);

			s = new StringItem("Chat", "t.me/nnmidletschat");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);
			display(f);
			return;
		}
		if (c == refreshCmd) {
			if (d instanceof MPForm) {
				((MPForm) d).cancel();
				((MPForm) d).load();
				return;
			}
			if (d instanceof MPList) {
				((MPList) d).cancel();
				((MPList) d).load();
				return;
			}
				
			return;
		}
		if (c == writeCmd) {
			display(writeForm(((ChatForm) d).id, null));
			return;
		}
		{ // auth
			if (c == authCmd) {
				if (d instanceof TextBox) {
					user = ((TextBox) d).getString();
					if (user.length() < 32) {
						display(errorAlert(""), null);
						return;
					}
					writeAuth();
					
					display(loadingAlert("Waiting for server response.."), null);
					start(RUN_VALIDATE_AUTH, user);
					return;
				}
				Alert a = new Alert("", "Choose authorization method", null, null);
				a.addCommand(authImportSessionCmd);
				a.addCommand(authNewSessionCmd);
				a.setCommandListener(this);
				
				display(a, null);
				return;
			}
			if (c == authImportSessionCmd) {
				TextBox t = new TextBox("User code", user == null ? "" : user, 200, TextField.NON_PREDICTIVE);
				t.addCommand(cancelCmd);
				t.addCommand(authCmd);
				t.setCommandListener(this);
				
				display(t);
				return;
			}
			if (c == authNewSessionCmd) {
				TextBox t = new TextBox("Phone number", phone == null ? "" : phone, 30, TextField.PHONENUMBER);
				t.addCommand(cancelCmd);
				t.addCommand(nextCmd);
				t.setCommandListener(this);
				
				display(t);
				return;
			}
			if (c == nextCmd) {
				if (d instanceof TextBox) {
					// phone number
					phone = ((TextBox) d).getString();
					if (phone.length() < 10 && !phone.startsWith("+")) {
						display(errorAlert(""), null);
						return;
					}
					writeAuth();
					
					CaptchaForm f = new CaptchaForm();
					start(RUN_LOAD_FORM, f);
					display(f);
					return;
				}
				return;
			}
		}
		if (c == backCmd || c == cancelCmd) {
			if (formHistory.size() == 0) {
				display(null, true);
				return;
			}
			Displayable p = null;
			synchronized (formHistory) {
				int i = formHistory.size();
				while (i-- != 0) {
					if (formHistory.elementAt(i) == d) {
						break;
					}
				}
				if (i > 0) {
					p = (Displayable) formHistory.elementAt(i - 1);
					formHistory.removeElementAt(i);
				}
			}
			display(p, true);
			return;
		}
		if (c == exitCmd) {
			notifyDestroyed();
		}
	}
	
	public void commandAction(Command c, Item item) {
		if (c == itemChatCmd) {
			String[] s = (String[]) ((MPForm) current).urls.get(item);
			if (s == null) return;
			openChat(s[0]);
			return;
		}
		if (c == itemChatInfoCmd) {
			String[] s = (String[]) ((MPForm) current).urls.get(item);
			if (s == null) return;
			openChatInfo(s[0]);
			return;
		}
		if (c == replyMsgCmd) {
			String[] s = (String[]) ((MPForm) current).urls.get(item);
			if (s == null) return;
			display(writeForm(((ChatForm) current).id, s[1]));
			return;
		}
		if (c == forwardMsgCmd) {
			// TODO
			return;
		}
		if (c == copyMsgCmd) {
			String[] s = (String[]) ((MPForm) current).urls.get(item);
			if (s == null) return;
			copy("", (String) ((MPForm) current).urls.get(s[1]));
			return;
		}
		commandAction(c, display.getCurrent());
	}

	private static void writeAuth() {
		try {
			RecordStore.deleteRecordStore(AUTH_RECORDNAME);
		} catch (Exception ignored) {}
		try {
			JSONObject j = new JSONObject();
			
			j.put("user", user);
			j.put("state", userState);
			j.put("phone", phone);
			
			byte[] b = j.toString().getBytes("UTF-8");
			RecordStore r = RecordStore.openRecordStore(AUTH_RECORDNAME, true);
			r.addRecord(b, 0, b.length);
			r.closeRecordStore();
		} catch (Exception e) {}
	}
	
	static void queueAvatar(String id, Object target) {
		if (target == null || id == null || !loadAvatars) return;
		synchronized (avatarsLoadLock) {
			avatarsToLoad.addElement(new Object[] { id, target });
			avatarsLoadLock.notifyAll();
		}
	}

	static void fillPeersCache(JSONObject users, JSONObject chats) {
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
	
	static StringBuffer appendOneLine(StringBuffer sb, String s) {
		if (s == null) return sb;
		int i = 0, l = s.length();
		while (i < l && i < 64) {
			char c = s.charAt(i++);
			if (c == '\r') continue;
			if (c != '\n') sb.append(c);
			else sb.append(' ');
		}
		return sb;
	}
	
	static String getName(String id, boolean variant) {
		if (id == null) return null;
		String res;
		JSONObject o;
		if (id.charAt(0) == '-') {
			o = chatsCache.getObject(id, null);
			if (o == null) return null;
			res = o.getString("t");
		} else {
			o = usersCache.getObject(id, null);
			if (o == null) return null;
			res = variant ? getShortName(o) : getName(o);
		}
		return res;
	}
	
	static String getName(JSONObject p) {
		if (p == null) return null;
		if (p.has("t")) {
			return p.getString("t");
		}
		
		String fn = p.getString("fn");
		String ln = p.getString("ln");
		
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
		if (p.has("t")) {
			return p.getString("t");
		}
		
		String fn = p.getString("fn");
		String ln = p.getString("ln");
		
		if (fn != null) {
			return fn;
		}
		
		if (ln != null) {
			return ln;
		}
		
		return "Deleted";
	}
	
	static ChatsList mainChatsList() {
		ChatsList l = chatsList = new ChatsList("Chats", 0);
		l.removeCommand(backCmd);
		l.addCommand(exitCmd);
		l.addCommand(aboutCmd);
		l.addCommand(settingsCmd);
		return l;
	}
	
	static Form initialAuthForm() {
		Form f = new Form("Auth");
		f.addCommand(exitCmd);
		f.addCommand(aboutCmd);
		f.addCommand(settingsCmd);
		f.setCommandListener(midlet);
		
		TextField t = new TextField("Instance URL", instanceUrl, 200, TextField.URL);
		instanceField = t;
		f.append(t);
		
		t = new TextField("Instance URL", instanceUrl, 200, TextField.URL);
		instancePasswordField = t;
		f.append(t);
		
		StringItem s = new StringItem(null, "Auth", StringItem.BUTTON);
		s.setDefaultCommand(authCmd);
		s.setItemCommandListener(midlet);
		
		return f;
	}
	
	static Form writeForm(String id, String reply) {
		Form f = new Form("Write");
		f.setCommandListener(midlet);
		f.addCommand(backCmd);
		f.addCommand(sendCmd);
		
		// TODO
		
		return f;
	}
	
	static void openChat(String id) {
		Form f = new ChatForm(id);
		display(f);
		midlet.start(RUN_LOAD_FORM, f);
	}
	
	static void openChatInfo(String id) {
		Form f = new ChatInfoForm(id);
		display(f);
		midlet.start(RUN_LOAD_FORM, f);
	}
	
	static void copy(String title, String text) {
		// TODO use nokiaui?
		TextBox t = new TextBox(title, text, text.length() + 1, TextField.UNEDITABLE);
		t.addCommand(backCmd);
		t.setCommandListener(midlet);
		display(t);
	}
	
	static void display(Alert a, Displayable d) {
		if (d == null) {
			display.setCurrent(a);
			return;
		}
		if (display.getCurrent() != d) {
			display(d);
		}
		display.setCurrent(a, d);
	}
	
	static void display(Displayable d) {
		display(d, false);
	}

	static void display(Displayable d, boolean back) {
		if (d instanceof Alert) {
			display.setCurrent((Alert) d, mainDisplayable);
			return;
		}
		if (d == loadingForm) {
			display.setCurrent(d);
			return;
		}
		if (d == null || d == mainDisplayable) {
			d = mainDisplayable;
			
			formHistory.removeAllElements();
			avatarsToLoad.removeAllElements();
		}
		Displayable p = display.getCurrent();
		if (p == loadingForm) p = current;
		display.setCurrent(current = d);
		if (p == null || p == d) return;
		
		if (p instanceof MPForm) {
			((MPForm) p).closed(back);
		}
		// push to history
		if (!back && d != mainDisplayable && (formHistory.isEmpty() || formHistory.lastElement() != d)) {
			formHistory.addElement(d);
		}
	}

	static Alert errorAlert(String text) {
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

	private static Alert loadingAlert(String s) {
		Alert a = new Alert("", s, null, null);
		a.setCommandListener(midlet);
		a.addCommand(Alert.DISMISS_COMMAND);
		a.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
		a.setTimeout(Alert.FOREVER);
		return a;
	}
	
	static Object api(String url) throws IOException {
		Object res;

		HttpConnection hc = null;
		InputStream in = null;
		try {
			String t = instanceUrl.concat(API_URL + "?v=" + API_VERSION + "&method=").concat(url);
			hc = openHttpConnection(t);
			hc.setRequestMethod("GET");
			
			int c = hc.getResponseCode();
			if (c == 502) {
				// repeat
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e.toString());
				}
				
				hc = openHttpConnection(t);
				hc.setRequestMethod("GET");
				
				c = hc.getResponseCode();
			}
			try {
				res = JSONStream.getStream(in = hc.openInputStream()).nextValue();
			} catch (RuntimeException e) {
				if (c >= 400) {
					throw new APIException(url, c, null);
				} else throw e;
			}
			if (c >= 400 || (res instanceof JSONObject && ((JSONObject) res).has("error"))) {
				throw new APIException(url, c, res);
			}
		} finally {
			if (in != null) try {
				in.close();
			} catch (IOException e) {}
			if (hc != null) try {
				hc.close();
			} catch (IOException e) {}
		}
		System.out.println(res instanceof JSONObject ?
				((JSONObject) res).format(0) : res instanceof JSONArray ?
						((JSONArray) res).format(0) : res);
		return res;
	}

	static JSONStream apiStream(String url) throws IOException {
		JSONStream res = null;

		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = openHttpConnection(instanceUrl.concat(API_URL + "?v=" + API_VERSION + "&method=").concat(url));
			hc.setRequestMethod("GET");
			
			int c = hc.getResponseCode();
			if (c >= 400) {
				throw new APIException(url, c, null);
			}
			res = JSONStream.getStream(hc);
		} finally {
			if (res == null) {
				if (in != null) try {
					in.close();
				} catch (IOException e) {}
				if (hc != null) try {
					hc.close();
				} catch (IOException e) {}
			}
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
	
	private static byte[] get(String url) throws IOException {
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = openHttpConnection(url);
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
	
	private static HttpConnection openHttpConnection(String url) throws IOException {
		System.out.println(url);
		HttpConnection hc = (HttpConnection) Connector.open(url);
		hc.setRequestProperty("User-Agent", "mpgram4/".concat(version));
		if (url.startsWith(instanceUrl)) {
			if (user != null) {
				hc.setRequestProperty("X-mpgram-user", user);
			}
			hc.setRequestProperty("X-mpgram-app-version", version);
			if (instancePassword != null) {
				hc.setRequestProperty("X-mpgram-instance-password", instancePassword);
			}
		}
		return hc;
	}
	
	public static String url(String url) {
		return appendUrl(new StringBuffer(), url).toString();
	}

	public static StringBuffer appendUrl(StringBuffer sb, String url) {
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
				sb.append('%');
				byte b = (byte) c;
				sb.append(Integer.toHexString(b >> 4 & 0xf));
				sb.append(Integer.toHexString(b & 0xf));
			} else if (c <= 2047) {
				sb.append('%');
				byte b = (byte) (0xC0 | c >> 6);
				sb.append(Integer.toHexString(b >> 4 & 0xf));
				sb.append(Integer.toHexString(b & 0xf));
				
				sb.append('%');
				b = (byte) (0x80 | c & 0x3F);
				sb.append(Integer.toHexString(b >> 4 & 0xf));
				sb.append(Integer.toHexString(b & 0xf));
			} else {
				sb.append('%');
				byte b = (byte) (0xE0 | c >> 12);
				sb.append(Integer.toHexString(b >> 4 & 0xf));
				sb.append(Integer.toHexString(b & 0xf));
				
				sb.append('%');
				b = (byte) (0x80 | c >> 6 & 0x3F);
				sb.append(Integer.toHexString(b >> 4 & 0xf));
				sb.append(Integer.toHexString(b & 0xf));
				
				sb.append('%');
				b = (byte) (0x80 | c & 0x3F);
				sb.append(Integer.toHexString(b >> 4 & 0xf));
				sb.append(Integer.toHexString(b & 0xf));
			}
		}
		return sb;
	}
	
	static StringBuffer appendTime(StringBuffer sb, long date) {
		date = (date + tzOffset) / 60;
		return sb.append(n(((int) date / 60) % 24))
				.append(':')
				.append(n((int) date % 60));
	}
	
	static String n(int n) {
		if (n < 10) {
			return "0".concat(Integer.toString(n));
		} else return Integer.toString(n);
	}
	
	private static final int
			RT_BOLD = 0,
			RT_ITALIC = 1,
			RT_PRE = 2,
			RT_UNDERLINE = 3,
			RT_STRIKE = 4,
			RT_SPOILER = 5,
			RT_URL = 6;

	static int wrapRichText(MPForm form, Thread thread, String text, JSONArray entities, int insert) {
		return wrapRichText(form, thread, text, entities, insert, new int[8]);
	}
	
	private static int wrapRichNestedText(MPForm form, Thread thread, String text, JSONObject entity, JSONArray allEntities, int insert, int[] state) {
		int off = entity.getInt("offset");
		int len = entity.getInt("length");
		JSONArray entities = new JSONArray();
		
		int l = allEntities.size();
		for (int i = 0; i < l; ++i) {
			JSONObject e = allEntities.getObject(i);
			if (e == entity) continue;
			if (e.getInt("offset") >= off && e.getInt("offset")+e.getInt("length") <= off+len) {
				JSONObject ne = new JSONObject();
				for(Enumeration en = e.keys(); en.hasMoreElements(); ) {
					String k = (String) en.nextElement();
					ne.put(k, ne.get(k));
				}
				
				ne.put("offset", ne.getInt("offset") - off);
				entities.add(ne);
			}
		}
		
		if (entities.size() > 0) {
			return wrapRichText(form, thread, text, entities, insert, state);
		}
		return flush(form, thread, text, insert, state);
	}

	private static int wrapRichText(MPForm form, Thread thread, String text, JSONArray entities, int insert, int[] state) {
		int len = entities.size();
		int lastOffset = 0;
		for (int i = 0; i < len; ++i) {
			JSONObject entity = entities.getObject(i);
			if (entity.getInt("offset") > lastOffset) {
				insert = flush(form, thread, text.substring(lastOffset, entity.getInt("offset")), insert, state);
			} else if (entity.getInt("offset") < lastOffset) {
				continue;
			}
			boolean skipEntity = false;
			String entityText = text.substring(entity.getInt("offset"), entity.getInt("offset") + entity.getInt("length"));
			String type = entity.getString("_");
			if ("messageEntityUrl".equals(type)) {
				state[RT_URL] ++;
				insert = flush(form, thread, richTextUrl = entityText, insert, state);
				state[RT_URL] --;
			} else if ("messageEntityTextUrl".equals(type)) {
				state[RT_URL] ++;
				richTextUrl = entity.getString("url");
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_URL] --;
			} else if ("messageEntityBold".equals(type)) {
				state[RT_BOLD] ++;
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_BOLD] --;
			} else if ("messageEntityItalic".equals(type)) {
				state[RT_ITALIC] ++;
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_ITALIC] --;
			} else if ("messageEntityCode".equals(type) || "messageEntityPre".equals(type)) {
				state[RT_PRE] ++;
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_PRE] --;
			} else if ("messageEntityUnderline".equals(type)) {
				state[RT_UNDERLINE] ++;
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_UNDERLINE] --;
			} else if ("messageEntityStrike".equals(type)) {
				state[RT_STRIKE] ++;
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_STRIKE] --;
			} else if ("messageEntitySpoiler".equals(type)) {
				state[RT_SPOILER] ++;
				insert = wrapRichNestedText(form, thread, entityText, entity, entities, insert, state);
				state[RT_SPOILER] --;
			} else {
				skipEntity = true;
			}
			lastOffset = entity.getInt("offset") + (skipEntity ? 0 : entity.getInt("length"));
		}
		
		return flush(form, thread, text.substring(lastOffset), insert, state);
	}
	
	private static int flush(MPForm form, Thread thread, String text, int insert, int[] state) {
		StringItem s = new StringItem(null, text);
		s.setFont(getFont(state));
		if (state[RT_URL] != 0) {
			form.urls.put(s, richTextUrl);
			s.setDefaultCommand(richTextLinkCmd);
			s.setItemCommandListener(midlet);
		}
		form.safeInsert(thread, insert++, s);
		
		return insert;
	}

	private static Font getFont(int[] state) {
		int face = 0, style = 0, size = Font.SIZE_SMALL;
		if (state[RT_PRE] != 0) {
			face = Font.FACE_MONOSPACE;
			style = Font.STYLE_BOLD;
			size = Font.SIZE_SMALL;
		} else {
			if (state[RT_BOLD] != 0) {
				style |= Font.STYLE_BOLD;
			}
			if (state[RT_ITALIC] != 0) {
				style |= Font.STYLE_ITALIC;
			}
			if (state[RT_UNDERLINE] != 0) {
				style |= Font.STYLE_UNDERLINED;
			}
			// there is no strikethrough font in midp
//			if (state[RT_STRIKE] != 0) {
//				style |= Font.STYLE_UNDERLINED;
//			}
		}
		return getFont(face, style, size);
	}

	private static Font getFont(int face, int style, int size) {
		if (face == 0) {
//			int setSize = fontSize;
//			if (setSize == 0) {
//				size = size == Font.SIZE_LARGE ? Font.SIZE_MEDIUM : Font.SIZE_SMALL;
//			} else if (setSize == 2) {
//				size = size == Font.SIZE_SMALL ? Font.SIZE_MEDIUM : Font.SIZE_LARGE;
//			}
			
			if (size == Font.SIZE_SMALL) {
				if (style == Font.STYLE_BOLD) {
					return smallBoldFont;
				}
				if (style == Font.STYLE_ITALIC) {
					return smallItalicFont;
				}
				if (style == Font.STYLE_PLAIN) {
					return smallPlainFont;
				}
			} else if (size == Font.SIZE_MEDIUM) {
				if (style == Font.STYLE_BOLD) {
					return medBoldFont;
				}
				if (style == Font.STYLE_ITALIC) {
					return medItalicFont;
				}
				if (style == (Font.STYLE_BOLD | Font.STYLE_ITALIC)) {
					return medItalicBoldFont;
				}
				if (style == Font.STYLE_PLAIN) {
					return medPlainFont;
				}
			}
			if (size == Font.SIZE_LARGE && style == Font.STYLE_PLAIN) {
				return largePlainFont;
			}
		}
		return Font.getFont(face, style, size);
	}

}
