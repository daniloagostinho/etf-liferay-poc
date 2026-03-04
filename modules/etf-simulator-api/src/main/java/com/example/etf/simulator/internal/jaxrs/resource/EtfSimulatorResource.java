package com.example.etf.simulator.internal.jaxrs.resource;

import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import java.net.HttpURLConnection;
import java.net.URL;

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

	@GET
	@Path("/health")
	@Produces(MediaType.APPLICATION_JSON)
	public Response health() {
		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put("status", "ok");
		response.put("backend", "liferay-osgi-jaxrs");
		response.put("elasticsearch", _getElasticsearchHealth());

		return Response.ok(response.toString()).build();
	}

	@GET
	@Path("/etfs")
	@Produces(MediaType.APPLICATION_JSON)
	public Response etfs() {
		JSONArray etfs = JSONFactoryUtil.createJSONArray();

		etfs.put(_etf("BOVV11", "Renda Variável", 0.115, 0.22, 0.003));
		etfs.put(_etf("SMAC11", "Renda Variável Internacional", 0.095, 0.18, 0.005));
		etfs.put(_etf("IMAB11", "Renda Fixa", 0.075, 0.07, 0.0025));
		etfs.put(_etf("BITI11", "Criptoativos", 0.16, 0.45, 0.008));

		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put("items", etfs);
		response.put("count", etfs.length());

		return Response.ok(response.toString()).build();
	}

	@POST
	@Path("/simulate")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response simulate(String payload) {
		JSONObject request;

		try {
			request = JSONFactoryUtil.createJSONObject(payload);
		}
		catch (Exception exception) {
			JSONObject error = JSONFactoryUtil.createJSONObject();
			error.put("error", "Payload JSON inválido");
			return Response.status(Response.Status.BAD_REQUEST).entity(error.toString()).build();
		}

		double aporteInicial = request.getDouble("aporteInicial", 10000);
		double aporteMensal = request.getDouble("aporteMensal", 500);
		int prazoMeses = request.getInt("prazoMeses", 60);
		double taxaAnualEsperada = request.getDouble("taxaAnualEsperada", 0.10);
		double taxaAdministracaoAnual = request.getDouble("taxaAdministracaoAnual", 0.004);

		double taxaLiquidaAnual = Math.max(
			0,
			taxaAnualEsperada - taxaAdministracaoAnual
		);

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

		return Response.ok(response.toString()).build();
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

	private JSONObject _getElasticsearchHealth() {
		JSONObject es = JSONFactoryUtil.createJSONObject();

		es.put("endpoint", "http://127.0.0.1:9201/_cluster/health");
		es.put("reachable", false);

		try {
			URL url = new URL("http://127.0.0.1:9201/_cluster/health");
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