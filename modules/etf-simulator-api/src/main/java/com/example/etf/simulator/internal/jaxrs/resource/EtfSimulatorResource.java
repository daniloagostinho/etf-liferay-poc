package com.example.etf.simulator.internal.jaxrs.resource;

import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.osgi.service.component.annotations.Component;

@Component(
	property = {
		"osgi.jaxrs.application.select=(osgi.jaxrs.name=EtfSimulator.Rest)",
		"osgi.jaxrs.resource=true"
	},
	service = EtfSimulatorResource.class
)
@Path("/v1")
public class EtfSimulatorResource {

	private static final String _BENCHMARKS_INDEX = "etf_poc_benchmarks";
	private static final String _ES_BASE_URL = "http://127.0.0.1:9201";
	private static final String _ETFS_INDEX = "etf_poc_etfs";
	private static final String _SIMULATIONS_INDEX = "etf_poc_simulations";

	@GET
	@Path("/health")
	@Produces(MediaType.APPLICATION_JSON)
	public Response health() {
		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put("status", "ok");
		response.put("backend", "liferay-osgi-jaxrs");
		response.put("elasticsearch", _getElasticsearchHealth());
		response.put("pocWarning", _pocWarning());

		return Response.ok(response.toString()).build();
	}

	@GET
	@Path("/etfs")
	@Produces(MediaType.APPLICATION_JSON)
	public Response etfs() {
		JSONArray etfs = _loadEtfs();

		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put("items", etfs);
		response.put("count", etfs.length());
		response.put("pocWarning", _pocWarning());

		return Response.ok(response.toString()).build();
	}

	@GET
	@Path("/benchmarks")
	@Produces(MediaType.APPLICATION_JSON)
	public Response benchmarks() {
		JSONArray items = _loadBenchmarks();

		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put("items", items);
		response.put("count", items.length());
		response.put("pocWarning", _pocWarning());

		return Response.ok(response.toString()).build();
	}

	@GET
	@Path("/simulations")
	@Produces(MediaType.APPLICATION_JSON)
	public Response simulations() {
		JSONArray simulations = _loadSimulations();

		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put("items", simulations);
		response.put("count", simulations.length());
		response.put("pocWarning", _pocWarning());

		return Response.ok(response.toString()).build();
	}

	@POST
	@Path("/simulate")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response simulate(String payload) {
		JSONObject request;

		if ((payload == null) || payload.trim().isEmpty()) {
			return _badRequest("Payload JSON obrigatório");
		}

		try {
			request = JSONFactoryUtil.createJSONObject(payload);
		}
		catch (Exception exception) {
			return _badRequest("Payload JSON inválido");
		}

		double aporteInicial = request.getDouble("aporteInicial", 10000);
		double aporteMensal = request.getDouble("aporteMensal", 500);
		int prazoMeses = request.getInt("prazoMeses", 60);
		double taxaAnualEsperada = request.getDouble("taxaAnualEsperada", 0.10);
		double taxaAdministracaoAnual = request.getDouble("taxaAdministracaoAnual", 0.004);

		List<String> validationErrors = _validateRequest(
			aporteInicial, aporteMensal, prazoMeses, taxaAnualEsperada,
			taxaAdministracaoAnual);

		if (!validationErrors.isEmpty()) {
			JSONObject error = JSONFactoryUtil.createJSONObject();
			JSONArray errors = JSONFactoryUtil.createJSONArray();

			for (String validationError : validationErrors) {
				errors.put(validationError);
			}

			error.put("error", "Parâmetros inválidos");
			error.put("errors", errors);
			return Response.status(Response.Status.BAD_REQUEST).entity(error.toString()).build();
		}

		double taxaLiquidaAnual = taxaAnualEsperada - taxaAdministracaoAnual;

		BigDecimal saldo = _toMoney(aporteInicial);
		BigDecimal totalInvestido = _toMoney(aporteInicial);
		BigDecimal mensal = _toMoney(aporteMensal);
		BigDecimal taxaMensal = BigDecimal.valueOf(
			Math.pow(1 + taxaLiquidaAnual, 1.0 / 12.0) - 1
		);

		JSONArray serieMensal = JSONFactoryUtil.createJSONArray();

		for (int mes = 1; mes <= prazoMeses; mes++) {
			saldo = saldo.multiply(BigDecimal.ONE.add(taxaMensal), MathContext.DECIMAL64);
			saldo = saldo.add(mensal);
			totalInvestido = totalInvestido.add(mensal);

			JSONObject ponto = JSONFactoryUtil.createJSONObject();
			ponto.put("mes", mes);
			ponto.put("saldo", _round(saldo));
			serieMensal.put(ponto);
		}

		BigDecimal ganho = saldo.subtract(totalInvestido);
		BigDecimal retornoPercentual = BigDecimal.ZERO;

		if (totalInvestido.compareTo(BigDecimal.ZERO) > 0) {
			retornoPercentual = ganho
				.divide(totalInvestido, 8, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100));
		}

		JSONObject response = JSONFactoryUtil.createJSONObject();
		response.put("aporteInicial", _round(_toMoney(aporteInicial)));
		response.put("aporteMensal", _round(mensal));
		response.put("prazoMeses", prazoMeses);
		response.put("taxaAnualEsperada", taxaAnualEsperada);
		response.put("taxaAdministracaoAnual", taxaAdministracaoAnual);
		response.put("taxaLiquidaAnual", taxaLiquidaAnual);
		response.put("valorFinal", _round(saldo));
		response.put("totalInvestido", _round(totalInvestido));
		response.put("ganhoEstimado", _round(ganho));
		response.put("retornoPercentual", _round(retornoPercentual));
		response.put("serieMensal", serieMensal);
		response.put("benchmarkDisclaimer", "Benchmarks comparativos podem estar em cenário hipotético na PoC.");

		JSONObject persistence = _persistSimulation(
			request, response, serieMensal
		);

		response.put("simulationPersistence", persistence);
		response.put("pocWarning", _pocWarning());

		return Response.ok(response.toString()).build();
	}

	private JSONObject _benchmark(String nome, double taxaAnualEsperada, boolean hipotetico) {
		JSONObject benchmark = JSONFactoryUtil.createJSONObject();

		benchmark.put("nome", nome);
		benchmark.put("taxaAnualEsperada", taxaAnualEsperada);
		benchmark.put("hipotetico", hipotetico);

		return benchmark;
	}

	private JSONObject _esRequest(String method, String path, String payload) {
		JSONObject result = JSONFactoryUtil.createJSONObject();

		HttpURLConnection connection = null;

		try {
			URL url = new URL(_ES_BASE_URL + path);
			connection = (HttpURLConnection)url.openConnection();

			connection.setConnectTimeout(2000);
			connection.setReadTimeout(10000);
			connection.setRequestMethod(method);
			connection.setRequestProperty("Content-Type", "application/json");

			if (payload != null) {
				connection.setDoOutput(true);

				try (OutputStream outputStream = connection.getOutputStream()) {
					outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
				}
			}

			int status = connection.getResponseCode();

			result.put("status", status);

			BufferedReader bufferedReader;

			if (status >= 400) {
				if (connection.getErrorStream() == null) {
					result.put("body", "");
					return result;
				}

				bufferedReader = new BufferedReader(
					new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
			}
			else {
				bufferedReader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			}

			StringBuilder stringBuilder = new StringBuilder();
			String line;

			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line);
			}

			bufferedReader.close();

			result.put("body", stringBuilder.toString());
		}
		catch (Exception exception) {
			result.put("status", 0);
			result.put("error", exception.getMessage());
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

		return result;
	}

	private JSONArray _extractSourceItems(JSONObject searchResponse) {
		JSONArray items = JSONFactoryUtil.createJSONArray();

		try {
			if (searchResponse.getInt("status") >= 400) {
				return items;
			}

			String body = searchResponse.getString("body");

			if ((body == null) || body.isEmpty()) {
				return items;
			}

			JSONObject bodyObject = JSONFactoryUtil.createJSONObject(body);
			JSONArray hits = bodyObject.getJSONObject("hits").getJSONArray("hits");

			for (int i = 0; i < hits.length(); i++) {
				JSONObject hit = hits.getJSONObject(i);
				items.put(hit.getJSONObject("_source"));
			}
		}
		catch (Exception exception) {
			return JSONFactoryUtil.createJSONArray();
		}

		return items;
	}

	private JSONArray _defaultBenchmarks() {
		JSONArray items = JSONFactoryUtil.createJSONArray();

		items.put(_benchmark("Ibovespa", 0.16, true));
		items.put(_benchmark("CDI", 0.12, true));

		return items;
	}

	private JSONArray _defaultEtfs() {
		JSONArray etfs = JSONFactoryUtil.createJSONArray();

		etfs.put(_etf("BOVV11", "Renda Variável", 0.115, 0.22, 0.003));
		etfs.put(_etf("SMAC11", "Renda Variável Internacional", 0.095, 0.18, 0.005));
		etfs.put(_etf("IMAB11", "Renda Fixa", 0.075, 0.07, 0.0025));
		etfs.put(_etf("BITI11", "Criptoativos", 0.16, 0.45, 0.008));

		return etfs;
	}

	private void _indexDefaultsIfNeeded(String index, JSONArray items) {
		JSONObject check = _esRequest(
			"GET", "/" + index + "/_count", null
		);

		if ((check.getInt("status") >= 200) && (check.getInt("status") < 300)) {
			try {
				JSONObject body = JSONFactoryUtil.createJSONObject(check.getString("body"));
				if (body.getInt("count") > 0) {
					return;
				}
			}
			catch (Exception exception) {
				return;
			}
		}

		for (int i = 0; i < items.length(); i++) {
			try {
				JSONObject item = items.getJSONObject(i);

				_esRequest(
					"POST", "/" + index + "/_doc?refresh=true",
					item.toString()
				);
			}
			catch (Exception exception) {
				return;
			}
		}
	}

	private boolean _isElasticsearchReachable() {
		JSONObject response = _esRequest("GET", "/_cluster/health", null);

		return (response.getInt("status") >= 200) && (response.getInt("status") < 300);
	}

	private JSONArray _loadBenchmarks() {
		if (!_isElasticsearchReachable()) {
			return _defaultBenchmarks();
		}

		JSONArray defaults = _defaultBenchmarks();

		_indexDefaultsIfNeeded(_BENCHMARKS_INDEX, defaults);

		JSONObject query = JSONFactoryUtil.createJSONObject();
		query.put("size", 20);

		JSONObject matchAll = JSONFactoryUtil.createJSONObject();
		matchAll.put("match_all", JSONFactoryUtil.createJSONObject());
		query.put("query", matchAll);

		JSONObject searchResponse = _esRequest(
			"POST", "/" + _BENCHMARKS_INDEX + "/_search", query.toString()
		);

		JSONArray items = _extractSourceItems(searchResponse);

		if (items.length() == 0) {
			return defaults;
		}

		return items;
	}

	private JSONArray _loadEtfs() {
		if (!_isElasticsearchReachable()) {
			return _defaultEtfs();
		}

		JSONArray defaults = _defaultEtfs();

		_indexDefaultsIfNeeded(_ETFS_INDEX, defaults);

		JSONObject query = JSONFactoryUtil.createJSONObject();
		query.put("size", 200);

		JSONObject matchAll = JSONFactoryUtil.createJSONObject();
		matchAll.put("match_all", JSONFactoryUtil.createJSONObject());
		query.put("query", matchAll);

		JSONObject searchResponse = _esRequest(
			"POST", "/" + _ETFS_INDEX + "/_search", query.toString()
		);

		JSONArray items = _extractSourceItems(searchResponse);

		if (items.length() == 0) {
			return defaults;
		}

		return items;
	}

	private JSONArray _loadSimulations() {
		if (!_isElasticsearchReachable()) {
			return JSONFactoryUtil.createJSONArray();
		}

		JSONObject query = JSONFactoryUtil.createJSONObject();
		query.put("size", 20);

		JSONArray sourceFields = JSONFactoryUtil.createJSONArray();
		sourceFields.put("createdAtEpochMs");
		sourceFields.put("request.aporteInicial");
		sourceFields.put("request.aporteMensal");
		sourceFields.put("request.prazoMeses");
		sourceFields.put("resultSummary.valorFinal");
		sourceFields.put("resultSummary.retornoPercentual");
		query.put("_source", sourceFields);

		JSONObject matchAll = JSONFactoryUtil.createJSONObject();
		matchAll.put("match_all", JSONFactoryUtil.createJSONObject());
		query.put("query", matchAll);

		JSONObject searchResponse = _esRequest(
			"POST", "/" + _SIMULATIONS_INDEX + "/_search", query.toString()
		);

		return _extractSourceItems(searchResponse);
	}

	private JSONObject _persistSimulation(
		JSONObject request, JSONObject response, JSONArray serieMensal) {

		JSONObject persistence = JSONFactoryUtil.createJSONObject();

		persistence.put("index", _SIMULATIONS_INDEX);
		persistence.put("saved", false);

		if (!_isElasticsearchReachable()) {
			persistence.put("reason", "elasticsearch-unreachable");
			return persistence;
		}

		JSONObject document = JSONFactoryUtil.createJSONObject();

		long now = System.currentTimeMillis();

		document.put("createdAtEpochMs", now);
		document.put("createdAtISO", String.valueOf(now));
		document.put("request", request);
		document.put("resultSummary", JSONFactoryUtil.createJSONObject()
			.put("valorFinal", response.getDouble("valorFinal"))
			.put("totalInvestido", response.getDouble("totalInvestido"))
			.put("ganhoEstimado", response.getDouble("ganhoEstimado"))
			.put("retornoPercentual", response.getDouble("retornoPercentual")));
		document.put("serieMensal", serieMensal);

		JSONObject esResponse = _esRequest(
			"POST", "/" + _SIMULATIONS_INDEX + "/_doc?refresh=true", document.toString());

		int status = esResponse.getInt("status");

		if ((status >= 200) && (status < 300)) {
			persistence.put("saved", true);

			try {
				JSONObject body = JSONFactoryUtil.createJSONObject(esResponse.getString("body"));
				persistence.put("documentId", body.getString("_id"));
			}
			catch (Exception exception) {
				persistence.put("documentId", "unknown");
			}
		}
		else {
			persistence.put("status", status);
			persistence.put("reason", "elasticsearch-index-error");
		}

		return persistence;
	}

	private JSONObject _etf(
		String ticker, String classe, double retornoAnual, double volatilidadeAnual,
		double taxaAdmAnual) {

		JSONObject etf = JSONFactoryUtil.createJSONObject();

		etf.put("ticker", ticker);
		etf.put("classe", classe);
		etf.put("retornoAnualEsperado", retornoAnual);
		etf.put("volatilidadeAnual", volatilidadeAnual);
		etf.put("taxaAdministracaoAnual", taxaAdmAnual);

		return etf;
	}

	private Response _badRequest(String message) {
		JSONObject error = JSONFactoryUtil.createJSONObject();

		error.put("error", message);

		return Response.status(Response.Status.BAD_REQUEST).entity(error.toString()).build();
	}

	private String _pocWarning() {
		return "Endpoint aberto para PoC local. Não utilizar em produção.";
	}

	private List<String> _validateRequest(
		double aporteInicial, double aporteMensal, int prazoMeses,
		double taxaAnualEsperada, double taxaAdministracaoAnual) {

		List<String> errors = new ArrayList<>();

		if ((aporteInicial < 0) || (aporteInicial > 100000000)) {
			errors.add("aporteInicial deve estar entre 0 e 100000000");
		}

		if ((aporteMensal < 0) || (aporteMensal > 10000000)) {
			errors.add("aporteMensal deve estar entre 0 e 10000000");
		}

		if ((prazoMeses < 1) || (prazoMeses > 480)) {
			errors.add("prazoMeses deve estar entre 1 e 480");
		}

		if ((taxaAnualEsperada < -0.95) || (taxaAnualEsperada > 1.0)) {
			errors.add("taxaAnualEsperada deve estar entre -0.95 e 1.0");
		}

		if ((taxaAdministracaoAnual < 0) || (taxaAdministracaoAnual > 0.2)) {
			errors.add("taxaAdministracaoAnual deve estar entre 0 e 0.2");
		}

		return errors;
	}

	private JSONObject _getElasticsearchHealth() {
		JSONObject es = JSONFactoryUtil.createJSONObject();

		es.put("endpoint", _ES_BASE_URL + "/_cluster/health");
		es.put("reachable", false);

		try {
			URL url = new URL(_ES_BASE_URL + "/_cluster/health");
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();

			connection.setConnectTimeout(1500);
			connection.setReadTimeout(1500);
			connection.setRequestMethod("GET");

			int code = connection.getResponseCode();
			es.put("httpStatus", code);

			BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(connection.getInputStream()));
			StringBuilder stringBuilder = new StringBuilder();
			String line;

			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line);
			}

			bufferedReader.close();

			JSONObject cluster = JSONFactoryUtil.createJSONObject(
				stringBuilder.toString());

			es.put("reachable", true);
			es.put("clusterName", cluster.getString("cluster_name"));
			es.put("status", cluster.getString("status"));
		}
		catch (Exception exception) {
			es.put("error", exception.getMessage());
		}

		return es;
	}

	private double _round(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

	private BigDecimal _toMoney(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
	}

}