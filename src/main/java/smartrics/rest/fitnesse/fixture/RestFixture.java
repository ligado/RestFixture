/*  Copyright 2008 Fabrizio Cannizzo
 *
 *  This file is part of RestFixture.
 *
 *  RestFixture (http://code.google.com/p/rest-fixture/) is free software:
 *  you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation,
 *  either version 3 of the License, or (at your option) any later version.
 *
 *  RestFixture is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with RestFixture.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  If you want to contact the author please leave a comment here
 *  http://smartrics.blogspot.com/2008/08/get-fitnesse-with-some-rest.html
 */
package smartrics.rest.fitnesse.fixture;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathConstants;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import smartrics.rest.client.RestClient;
import smartrics.rest.client.RestClientImpl;
import smartrics.rest.client.RestData.Header;
import smartrics.rest.client.RestRequest;
import smartrics.rest.client.RestResponse;
import smartrics.rest.config.Config;
import smartrics.rest.fitnesse.fixture.support.BodyTypeAdapter;
import smartrics.rest.fitnesse.fixture.support.BodyTypeAdapterFactory;
import smartrics.rest.fitnesse.fixture.support.ContentType;
import smartrics.rest.fitnesse.fixture.support.HeadersTypeAdapter;
import smartrics.rest.fitnesse.fixture.support.HttpClientBuilder;
import smartrics.rest.fitnesse.fixture.support.RestDataTypeAdapter;
import smartrics.rest.fitnesse.fixture.support.StatusCodeTypeAdapter;
import smartrics.rest.fitnesse.fixture.support.StringTypeAdapter;
import smartrics.rest.fitnesse.fixture.support.Tools;
import smartrics.rest.fitnesse.fixture.support.Url;
import smartrics.rest.fitnesse.fixture.support.Variables;
import fit.ActionFixture;
import fit.Fixture;
import fit.Parse;
import fit.exception.FitFailureException;

/**
 * A fixture that allows to simply test REST APIs with minimal efforts. The core
 * principles underpinning this fixture are:
 * <ul>
 * <li>allowing documentation of a REST API by showing how the API looks like.
 * For REST this means
 * <ul>
 * <li>show what the resource URI looks like. For example
 * <code>/resource-a/123/resource-b/234</code>
 * <li>show what HTTP operation is being executed on that resource. Specifically
 * which one fo the main HTTP verbs where under test (GET, POST, PUT, DELETE,
 * HEAD, OPTIONS).
 * <li>have the ability to set headers and body in the request
 * <li>check expectations on the return code of the call in order to document
 * the behaviour of the API
 * <li>check expectation on the HTTP headers and body in the response. Again, to
 * document the behaviour
 * </ul>
 * <li>should work without the need to write/maintain java code: tests are
 * written in wiki syntax.
 * <li>tests should be easy to write and above all read.
 * </ul>
 * 
 * <b>Configuring RestFixture</b><br/>
 * RestFixture can be configured by using the {@link RestFixtureConfig}. A
 * {@code RestFixtureConfig} can define named maps with configuration key/value
 * pairs. The name of the map is passed as second parameter to the
 * {@code RestFixture}. Using a named configuration is optional: if no name is
 * passed, the default configuration map is used. See {@link RestFixtureConfig}
 * for more details.
 * <p/>
 * The following list of configuration parameters can are supported.
 * <p/>
 * <table border="1">
 * <tr>
 * <td>smartrics.rest.fitnesse.fixture.RestFixtureConfig</td>
 * <td><i>optional named config</i></td>
 * </tr>
 * <tr>
 * <td>http.proxy.host</td>
 * <td><i>http proxy host name (RestClient proxy configutation)</i></td>
 * </tr>
 * <tr>
 * <td>http.proxy.port</td>
 * <td><i>http proxy host port (RestClient proxy configutation)</i></td>
 * </tr>
 * <tr>
 * <td>http.basicauth.username</td>
 * <td><i>username for basic authentication (RestClient proxy configutation)</i>
 * </td>
 * </tr>
 * <tr>
 * <td>http.basicauth.password</td>
 * <td><i>password for basic authentication (RestClient proxy configutation)</i>
 * </td>
 * </tr>
 * <tr>
 * <td>http.client.connection.timeout</td>
 * <td><i>client timeout for http connection (default 3s). (RestClient proxy
 * configutation)</i></td>
 * </tr>
 * <tr>
 * <td>restfixture.display.actual.on.right</td>
 * <td><i>boolean value. if true, the actual value of the header or body in an
 * expectation cell is displayed even when the expectation is met.</i></td>
 * </tr>
 * <tr>
 * <td>restfixture.default.headers</td>
 * <td><i>comma separated list of key value pairs representing the default list
 * of headers to be passed for each request. key and values are separated by a
 * colon. Entries are sepatated by {@code System.getProperty("line.separator")}.
 * {@link RestFixture#setHeader()} will override this value. </i></td>
 * </tr>
 * <tr>
 * <td>restfixture.xml.namespaces.context</td>
 * <td><i>comma separated list of key value pairs representing namespace
 * declarations. The key is the namespace alias, the value is the namespace URI.
 * alias and URI are separated by a = sign. Entries are sepatated by
 * {@code System.getProperty("line.separator")}. These entries will be used to
 * define the namespace context to be used in xpaths that are evaluated in the
 * results.</i></td>
 * </tr>
 * </table>
 * 
 * @author smartrics
 */
public class RestFixture extends ActionFixture {

	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final String FILE = "file";

	private RestResponse lastResponse;

	private RestRequest lastRequest;

	private String fileName = null;

	private String multipartFileName = null;

	private String multipartFileParameterName = FILE;

	private String requestBody;

	private Map<String, String> requestHeaders;

	protected RestClient restClient;

	protected Config config;

	private boolean displayActualOnRight;

	/**
	 * the headers passed to each request by default.
	 */
	private Map<String, String> defaultHeaders = new HashMap<String, String>();
	private Map<String, String> namespaceContext = new HashMap<String, String>();

    private static final Pattern FIND_VARS_PATTERN = Pattern.compile("\\%([a-zA-Z0-9_]+)\\%");
	private static Log LOG = LogFactory.getLog(RestFixture.class);
	private final static Variables variables = new Variables();

	private Url baseUrl;

	@SuppressWarnings("rawtypes")
	private RowWrapper row;

	@SuppressWarnings("rawtypes")
	private CellFormatter formatter;

	public RestFixture() {
		super();
		displayActualOnRight = true;
	}

	/**
	 * Slim constructor
	 * 
	 * @param args
	 *            the cells following up the first cell in the first row.
	 */
	public RestFixture(String host) {
		super();
		displayActualOnRight = true;
		initialize(new String[] { host });
	}

	public RestFixture(String host, String configName) {
		super();
		displayActualOnRight = true;
		initialize(new String[] { host, configName });
	}

	public Config getConfig() {
		return config;
	}

	public String getBaseUrl() {
		return baseUrl.toString();
	}

	/**
	 * The value of the flag controlling the display of the actual header or
	 * body in the cell containing the expectations.
	 *
	 * @return true if the actual value of the headers or body is displayed when
	 *         expectation is true
	 */
	public boolean isDisplayActualOnRight() {
		return displayActualOnRight;
	}

	/**
	 * Overrideable method that allows to redefine the rest client
	 * implementation.
	 */
	protected RestClient buildRestClient() {
        HttpClient httpClient = new HttpClientBuilder().createHttpClient(config);
		return new RestClientImpl(httpClient);
	}

	/**
	 * Slim Table table hook
	 * 
	 * @param rows
	 * @return
	 */
	public List<List<String>> doTable(List<List<String>> rows) {
		StringBuffer b = new StringBuffer();
		List<List<String>> res = new Vector<List<String>>();
		for (List<String> row : rows) {
			Vector<String> newRow = new Vector<String>();
			for (String cell : row) {
				b.append("|").append(cell);
				newRow.add("pass:" + cell);
				try {
					executeCell0Method(row.get(0));
				} catch (Exception e) {

				}
			}
			b.append("\n");
			res.add(newRow);
		}
		System.out.println(b.toString());
		return res;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void doCells(Parse parse) {
		this.row = new FitRow(parse);
		this.formatter = new FitFormatter(this);
		initialize(args);
        @SuppressWarnings("rawtypes")
        CellWrapper methodNameCell = this.row.getCell(0);
		try {
            executeCell0Method(methodNameCell.text());
		} catch (Exception exception) {
            formatter.exception(methodNameCell, exception);
		}
	}

	/**
	 * Configure the fixture with data from {@link RestFixtureConfig}.
	 */
	protected void configFixture() {
        displayActualOnRight = config.getAsBoolean("restfixture.display.actual.on.right", true);
		String str = config.get("restfixture.default.headers", "");
		defaultHeaders = parseHeaders(str);
		str = config.get("restfixture.xml.namespace.context", "");
		namespaceContext = parseNamespaceContext(str);
	}

	/**
	 * Overrideable method that allows to config the rest client implementation.
	 * the method shoudl configure the instance attribute
	 * {@link RestFixture#restClient} created by the
	 * {@link RestFixture#buildRestClient()}.
	 */
	protected void configRestClient() {
		restClient.setBaseUrl(baseUrl.toString());
	}

	/**
	 * Overrideable method to validate the state of the instance in execution. A
	 * {@link RestFixture} is valid if the baseUrl is not null.
	 *
	 * @return true if the state is valid, false otherwise
	 */
	protected boolean validateState() {
		return baseUrl != null;
	}

	/**
	 * Method invoked to notify that the state of the RestFixture is invalid. It
	 * throws a {@link FitFailureException} with a message displayed in the
	 * fitnesse page.
	 *
	 * @param state
	 *            as returned by {@link RestFixture#validateState()}
	 */
	protected void notifyInvalidState(boolean state) {
        if (!state) {
            throw new FitFailureException("You must specify a base url in the |start|, after the fixture to start");
        }
	}

	protected void processArguments(String[] args) {
		if (args.length > 0) {
			baseUrl = new Url(stripTag(args[0]));
			config = new Config();
		}
		if (args.length == 2) {
			config = new Config(args[1]);
		}
	}

	/**
	 * <code>| setMultipartFileName | Name of file |</code>
	 * <p/>
	 * body text should be location of file which needs to be sent
	 */
	public void setMultipartFileName() {
        if (row.getCell(1) == null) {
            throw new FitFailureException("You must pass a body to set");
        }
		multipartFileName = variables.substitute(row.getCell(0).text());
	}

	/**
	 * <code>| setFileName | Name of file |</code> <p/> body text should be
	 * location of file which needs to be sent
	 */
	public void setFileName() {
        if (row.getCell(1) == null) {
			throw new FitFailureException("You must pass a body to set");
        }
		fileName = variables.substitute(row.getCell(0).text());
	}

	/**
	 * <code>| setMultipartFileParameterName | Name of form parameter for the uploaded file |</code>
	 * <p/> body text should be the name of the form parameter, defaults to
	 * 'file'
	 */
	public void setMultipartFileParameterName() {
        if (row.getCell(1) == null)
			throw new FitFailureException(
					"You must pass a parameter name to set");
		multipartFileParameterName = variables
				.substitute(row.getCell(0).text());
	}

	/**
	 * <code>| setBody | body text goes here |</code> <p/> body text can either
	 * be a kvp or a xml. The <code>ClientHelper</code> will figure it out
	 */
	public void setBody() {
        CellWrapper<?> cell = row.getCell(1);
        if (cell == null)
			throw new FitFailureException("You must pass a body to set");
        body(variables.substitute(cell.text()));
	}

	/**
	 * <code>| setHeader | http headers go here as nvp |</code>
	 * <p/>
	 * header text must be nvp. name and value must be separated by ':' and each
	 * header is in its own line
	 */
	public void setHeader() {
        CellWrapper<?> cell = row.getCell(1);
        if (cell == null)
			throw new FitFailureException("You must pass a header map to set");
        String header = variables.substitute(cell.text());
		headers(header);
	}

	/**
	 * Equivalent to setHeader - syntactic sugar to indicate that you can now
	 * set multiple headers in a single call
	 */
	public void setHeaders() {
		setHeader();
	}

	/**
	 * <code> | PUT | uri | ?ret | ?headers | ?body |</code>
	 * <p/>
	 * executes a PUT on the uri and checks the return (a string repr the
	 * operation return code), the http response headers and the http response
	 * body
	 * 
	 * uri is resolved by replacing vars previously defined with
	 * <code>let()</code>
	 * 
	 * the http request headers can be set via <code>setHeaders()</code>. If not
	 * set, the list of default headers will be set. See
	 * <code>DEF_REQUEST_HEADERS</code>
	 */
	public void PUT() {
		debugMethodCallStart();
		doMethod(emptifyBody(requestBody), "Put");
		debugMethodCallEnd();
	}

	/**
	 * <code> | GET | uri | ?ret | ?headers | ?body |</code>
	 * <p/>
	 * executes a GET on the uri and checks the return (a string repr the
	 * operation return code), the http response headers and the http response
	 * body
	 *
	 * uri is resolved by replacing vars previously defined with
	 * <code>let()</code>
	 *
	 * the http request headers can be set via <code>setHeaders()</code>. If not
	 * set, the list of default headers will be set. See
	 * <code>DEF_REQUEST_HEADERS</code>
	 */
	public void GET() {
		debugMethodCallStart();
		doMethod("Get");
		debugMethodCallEnd();
	}

	/**
	 * <code> | DELETE | uri | ?ret | ?headers | ?body |</code>
	 * <p/>
	 * executes a DELETE on the uri and checks the return (a string repr the
	 * operation return code), the http response headers and the http response
	 * body
	 *
	 * uri is resolved by replacing vars previously defined with
	 * <code>let()</code>
	 *
	 * the http request headers can be set via <code>setHeaders()</code>. If not
	 * set, the list of default headers will be set. See
	 * <code>DEF_REQUEST_HEADERS</code>
	 */
	public void DELETE() {
		debugMethodCallStart();
		doMethod("Delete");
		debugMethodCallEnd();
	}

	/**
	 * <code> | POST | uri | ?ret | ?headers | ?body |</code>
	 * <p/>
	 * executes a POST on the uri and checks the return (a string repr the
	 * operation return code), the http response headers and the http response
	 * body
	 *
	 * uri is resolved by replacing vars previously defined with
	 * <code>let()</code>
	 *
	 * post requires a body that can be set via <code>setBody()</code>.
	 *
	 * the http request headers can be set via <code>setHeaders()</code>. If not
	 * set, the list of default headers will be set. See
	 * <code>DEF_REQUEST_HEADERS</code>
	 */
	public void POST() {
		debugMethodCallStart();
		doMethod(emptifyBody(requestBody), "Post");
		debugMethodCallEnd();
	}

	/**
	 * <code> | let | label | type | loc | expr |</code>
	 * <p/>
	 * allows to associate a value to a label. values are extracted from the
	 * body of the last successful http response.
	 * <ul>
	 * <li/><code>label</code> is the label identifier
	 *
	 * <li/><code>type</code> is the type of operation to perform on the last
	 * http response. At the moment only XPaths and Regexes are supported. In
	 * case of regular expressions, the expression must contain only one group
	 * match, if multiple groups are matched the label will be assigned to the
	 * first found <code>type</code> only allowed values are <code>xpath</code>
	 * and <code>regex</code>
	 *
	 * <li/><code>loc</code> where to apply the <code>expr</code> of the given
	 * <code>type</code>. Currently only <code>header</code> and
	 * <code>body</code> are supported. If type is <code>xpath</code> by default
	 * the expression is matched against the body and the value in loc is
	 * ignored.
	 *
	 * <li/><code>expr</code> is the expression of type <code>type</code> to be
	 * executed on the last http response to extract the content to be
	 * associated to the label.
	 * </ul>
	 * <p/>
	 * <code>label</code>s can be retrieved after they have been defined and
	 * their scope is the fixture instance under execution. They are stored in a
	 * map so multiple calls to <code>let()</code> with the same label will
	 * override the current value of that label.
	 * <p/>
	 * Labels are resolved in <code>uri</code>s, <code>header</code>s and
	 * <code>body</code>es.
	 * <p/>
	 * In order to be resolved a label must be between <code>%</code>, e.g.
	 * <code>%id%</code>.
	 * <p/>
	 * The test row must have an empy cell at the end that will display the
	 * value extracted and assigned to the label.
	 * <p/>
	 * Example: <br/>
	 * <code>| GET | /services | 200 | | |</code><br/>
	 * <code>| let | id |  body | /services/id[0]/text() | |</code><br/>
	 * <code>| GET | /services/%id% | 200 | | |</code>
	 * <p/>
	 * or
	 * <p/>
	 * <code>| POST | /services | 201 | | |</code><br/>
	 * <code>| let  | id | header | /services/([.]+) | |</code><br/>
	 * <code>| GET  | /services/%id% | 200 | | |</code>
	 */
	@SuppressWarnings("unchecked")
	public void let() {
		debugMethodCallStart();
        String label = row.getCell(1).text().trim();
        String loc = row.getCell(2).text();
        String expr = row.getCell(3).text();
		CellWrapper<?> valueCell = row.getCell(4);
		String sValue = null;
		try {
			if ("header".equals(loc)) {
				sValue = handleRegexExpression(label, loc, expr);
			} else if ("body".equals(loc)) {
				sValue = handleXpathExpression(label, expr);
			} else {
                throw new FitFailureException("let handles 'xpath' in body or 'regex' in headers.");
			}
			if (valueCell != null) {
				StringTypeAdapter adapter = new StringTypeAdapter();
				try {
					adapter.set(sValue);
					formatter.check(valueCell, adapter);
				} catch (Exception e) {
					formatter.exception(valueCell, e);
				}
			}
		} catch (IOException e) {
			formatter.exception(row.getCell(3), e);
		} catch (RuntimeException e) {
			formatter.exception(row.getCell(3), e);
		} finally {
			debugMethodCallEnd();
		}
	}

    private void executeCell0Method(String methodName) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		Method method1 = getClass().getMethod(methodName);
		method1.invoke(this);
	}

	protected void initialize(String[] args) {
		processArguments(args);
		boolean state = validateState();
		notifyInvalidState(state);
		configFixture();
		restClient = buildRestClient();
		configRestClient();
	}

    private String handleRegexExpression(String label, String loc, String expression) {
		List<String> content = new ArrayList<String>();
		if ("header".equals(loc)) {
			if (getLastResponse().getHeaders() != null) {
				for (Header e : getLastResponse().getHeaders()) {
                    String string = Tools.convertEntryToString(e.getName(), e.getValue(), ":");
					content.add(string);
				}
			}
		} else {
			content.add(getLastResponse().getBody());
		}
		String value = null;
		if (content.size() > 0) {
			Pattern p = Pattern.compile(expression);
			for (String c : content) {
				Matcher m = p.matcher(c);
				if (m.find()) {
					int cc = m.groupCount();
					value = m.group(cc);
					assignVariable(label, value);
					break;
				}
			}
		}
		return value;
	}

    private String handleXpathExpression(String label, String expr) throws IOException {
		// def. match only last response body
		String val = null;
		try {
			val = handleXPathAsNodeList(expr);
		} catch (IllegalArgumentException e) {
			// ignore - may be that it's eval to a string
		}
        if (val == null) {
			val = handleXPathAsString(expr);
        }
        if (val != null) {
			assignVariable(label, val);
        }
		return val;
	}

	private String handleXPathAsNodeList(String expr) {
        BodyTypeAdapter bodyTypeAdapter = BodyTypeAdapterFactory.getBodyTypeAdapter(getContentTypeOfLastResponse());
        NodeList list = Tools.extractXPath(namespaceContext, expr, bodyTypeAdapter.toXmlString(getLastResponse().getBody()));
		Node item = list.item(0);
		String val = null;
		if (item != null) {
			val = item.getTextContent();
		}
		return val;
	}

	private String handleXPathAsString(String expr) {
        String body = getLastResponse().getBody();
        if (body == null)
            throw new FitFailureException("'xpath' cannot be applied to body of last response because it's null.");
        String val = (String) Tools.extractXPath(namespaceContext, expr, body, XPathConstants.STRING);
		return val;
	}

	private String emptifyBody(String b) {
		String body = b;
        if (body == null) {
			body = "";
        }
		return body;
	}

	private void assignVariable(String label, String val) {
		String l = label;
		if (label.startsWith("$")) {
			l = label.substring(1);
			Fixture.setSymbol(l, val);
		} else {
			variables.put(label, val);
		}
	}

	public Map<String, String> getHeaders() {
		Map<String, String> headers = null;
		if (requestHeaders != null) {
			headers = requestHeaders;
		} else {
			headers = defaultHeaders;
		}
		return headers;
	}

	private void doMethod(String m) {
		doMethod(null, m);
	}

	private void doMethod(String body, String method) {
        String resUrl = resolve(FIND_VARS_PATTERN, row.getCell(1).text());
		setLastRequest(new RestRequest());
		getLastRequest().setMethod(RestRequest.Method.valueOf(method));
		getLastRequest().setFileName(fileName);
		getLastRequest().setMultipartFileName(multipartFileName);
        getLastRequest().setMultipartFileParameterName(multipartFileParameterName);
		getLastRequest().addHeaders(getHeaders());
		String uri[] = resUrl.split("\\?");
		getLastRequest().setResource(uri[0]);
		if (uri.length == 2) {
			getLastRequest().setQuery(uri[1]);
		}
		if ("Post".equals(method) || "Put".equals(method)) {
			String rBody = resolve(FIND_VARS_PATTERN, body);
			getLastRequest().setBody(rBody);
		}
		setLastResponse(restClient.execute(getLastRequest()));
		completeHttpMethodExecution();
	}

	private ContentType getContentTypeOfLastResponse() {
		return ContentType.parse(getLastResponse().getHeader("Content-Type"));
	}

	private void completeHttpMethodExecution() {
		String uri = getLastResponse().getResource();
		if (getLastRequest().getQuery() != null) {
			uri = uri + "?" + getLastRequest().getQuery();
		}
		String u = restClient.getBaseUrl() + uri;
        row.getCell(1).body("<a href='" + u + "'>" + uri + "</a>");
        process(row.getCell(2), getLastResponse().getStatusCode().toString(), new StatusCodeTypeAdapter());
        process(row.getCell(3), getLastResponse().getHeaders(), new HeadersTypeAdapter());
		row.getCell(4).body(resolve(FIND_VARS_PATTERN, row.getCell(4).body()));
        BodyTypeAdapter bodyTypeAdapter = BodyTypeAdapterFactory.getBodyTypeAdapter(getContentTypeOfLastResponse());
		bodyTypeAdapter.setContext(namespaceContext);
        process(row.getCell(4), getLastResponse().getBody(), bodyTypeAdapter);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
    private void process(CellWrapper expected, Object actual, RestDataTypeAdapter ta) {
		ta.set(actual);
		boolean ignore = "".equals(expected.text().trim());
		if (ignore) {
			expected.addToBody(formatter.gray(ta.toString()));
		} else {
			boolean success = false;
			try {
				success = ta.equals(ta.parse(variables.substitute(expected.text())), actual);
			} catch (Exception e) {
				formatter.exception(expected, e);
				return;
			}
			if (success) {
				right(expected, ta);
			} else {
				wrong(expected, ta);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void right(CellWrapper expected, RestDataTypeAdapter ta) {
		formatter.right(expected);
		if (isDisplayActualOnRight() && !expected.text().equals(ta.toString())) {
			expected.addToBody(formatter.label("expected") + "<hr>"
					+ ta.toString() + formatter.label("actual"));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void wrong(CellWrapper expected, RestDataTypeAdapter ta) {
		formatter.wrong(expected);
		StringBuffer sb = new StringBuffer();
		for (String e : ta.getErrors()) {
			sb.append(e).append(LINE_SEPARATOR);
		}
		expected.addToBody(formatter.label("expected") + "<hr>" + ta.toString()
				+ formatter.label("actual") + "<hr>"
				+ Tools.toHtml(sb.toString()) + formatter.label("errors"));
	}

	private void debugMethodCallStart() {
		debugMethodCall("=> ");
	}

	private void debugMethodCallEnd() {
		debugMethodCall("<= ");
	}

	private void debugMethodCall(String h) {
		StackTraceElement el = Thread.currentThread().getStackTrace()[4];
		LOG.debug(h + el.getMethodName());
	}

	private String resolve(Pattern pattern, String text) {
		Matcher m = pattern.matcher(text);
		Map<String, String> replacements = new HashMap<String, String>();
		while (m.find()) {
			int gc = m.groupCount();
			if (gc == 1) {
				String g0 = m.group(0);
				String g1 = m.group(1);
				String value = variables.get(g1);
				if (null == value) {
					Object o = Fixture.getSymbol(g1);
					if (null != o)
						value = o.toString();
				}
				replacements.put(g0, value);
			}
		}
		String newText = text;
		for (Entry<String, String> en : replacements.entrySet()) {
			String k = en.getKey();
			String replacement = replacements.get(k);
			if (replacement != null)
				newText = newText.replace(k, replacement);
		}
		return newText;
	}

	void body(String string) {
		requestBody = string;
	}

	void headers(String header) {
		requestHeaders = parseHeaders(header);
	}

	protected RestResponse getLastResponse() {
		return lastResponse;
	}

	protected RestRequest getLastRequest() {
		return lastRequest;
	}

	private void setLastResponse(RestResponse lastResponse) {
		this.lastResponse = lastResponse;
	}

	private void setLastRequest(RestRequest lastRequest) {
		this.lastRequest = lastRequest;
	}

	private Map<String, String> parseHeaders(String str) {
        return Tools.convertStringToMap(str, ":", LINE_SEPARATOR);
	}

	private Map<String, String> parseNamespaceContext(String str) {
        return Tools.convertStringToMap(str, "=", LINE_SEPARATOR);
	}

	private String stripTag(String somethingWithinATag) {
        return somethingWithinATag.replaceAll("<[^>]+>", "").replace("</a>", "");
	}

}
