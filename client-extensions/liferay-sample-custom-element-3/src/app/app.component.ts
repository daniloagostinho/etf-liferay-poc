/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {HttpClient} from '@angular/common/http';
import {Component, Input, OnInit} from '@angular/core';
import {ChartData, ChartOptions} from 'chart.js';

@Component({
	selector: 'liferay-sample-custom-element-3',
	styleUrls: ['./app.component.css'],
	templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
	@Input('title') title = 'liferay-sample-custom-element-3';

	benchmarkDisclaimer = '';
	benchmarks: Array<{nome: string; taxaAnualEsperada: number; hipotetico: boolean}> = [];
	errorMessage = '';
	etfs: any[] = [];
	health: any;
	healthLoaded = false;
	loading = false;
	simulationPersistence: any;
	simulations: any[] = [];
	simulationsLoaded = false;

	form = {
		aporteInicial: 10000,
		aporteMensal: 500,
		prazoMeses: 120,
		taxaAdministracaoAnual: 0.004,
		taxaAnualEsperada: 0.1,
	};

	result: any;

	lineChartData: ChartData<'line'> = {
		labels: [],
		datasets: [],
	};

	lineChartOptions: ChartOptions<'line'> = {
		animation: false,
		interaction: {
			intersect: false,
			mode: 'index',
		},
		maintainAspectRatio: false,
		plugins: {
			legend: {
				labels: {
					color: '#111827',
					padding: 20,
					usePointStyle: true,
				},
				position: 'bottom',
			},
			tooltip: {
				callbacks: {
					label: (context) =>
						`${context.dataset.label}: ${Number(context.parsed.y).toFixed(2)}%`,
				},
			},
		},
		scales: {
			x: {
				grid: {
					display: false,
				},
				ticks: {
					color: '#6b7280',
				},
			},
			y: {
				grid: {
					color: '#d1d5db',
				},
				ticks: {
					callback: (value) => `${value}%`,
					color: '#6b7280',
				},
			},
		},
	};

	periodOptions = [
		{label: '36M', months: 36},
		{label: '24M', months: 24},
		{label: '12M', months: 12},
		{label: '6M', months: 6},
		{label: '3M', months: 3},
		{label: 'Ano atual', months: new Date().getMonth() + 1},
	];

	selectedMonths = 36;
	fullChartPoints: {label: string; cdi: number; ibov: number; portfolio: number}[] = [];

	constructor(private http: HttpClient) {}

	ngOnInit(): void {
		this.loadHealth();
		this.loadBenchmarks();
		this.loadEtfs();
		this.loadSimulations();
		this.simulate();
	}

	loadSimulations() {
		this.http.get<any>('/o/etf-simulator/v1/simulations').subscribe({
			next: (response) => {
				this.simulations = response.items || [];
				this.simulationsLoaded = true;
			},
			error: (error) => {
				console.error(error);
				this.simulations = [];
				this.simulationsLoaded = true;
			},
		});
	}

	loadBenchmarks() {
		this.http.get<any>('/o/etf-simulator/v1/benchmarks').subscribe({
			next: (response) => {
				this.benchmarks = response.items || [];
				this.updateChart();
			},
			error: (error) => {
				console.error(error);
				this.benchmarks = [];
			},
		});
	}

	selectPeriod(months: number) {
		this.selectedMonths = months;
		this.updateChart();
	}

	loadEtfs() {
		this.http.get<any>('/o/etf-simulator/v1/etfs').subscribe({
			next: (response) => {
				this.etfs = response.items || [];
			},
			error: (error) => {
				console.error(error);
				this.errorMessage =
					'Não foi possível carregar ETFs. Verifique se o módulo backend foi deployado.';
			},
		});
	}

	loadHealth() {
		this.http.get<any>('/o/etf-simulator/v1/health').subscribe({
			next: (response) => {
				this.health = response;
				this.healthLoaded = true;
			},
			error: (error) => {
				console.error(error);
				this.healthLoaded = true;
			},
		});
	}

	simulate() {
		this.loading = true;
		this.errorMessage = '';

		this.http.post<any>('/o/etf-simulator/v1/simulate', this.form).subscribe({
			next: (response) => {
				this.result = response;
				this.benchmarkDisclaimer = response.benchmarkDisclaimer || '';
				this.simulationPersistence = response.simulationPersistence || null;
				this.buildChartSeries();
				this.updateChart();
				this.loadSimulations();
				this.loading = false;
			},
			error: (error) => {
				console.error(error);
				this.errorMessage =
					'Não foi possível executar a simulação. Verifique logs do Liferay.';
				this.loading = false;
			},
		});
	}

	private buildChartSeries() {
		if (!this.result?.serieMensal?.length) {
			this.fullChartPoints = [];
			return;
		}

		const serieMensal = this.result.serieMensal as Array<{mes: number; saldo: number}>;
		const aporteInicial = Number(this.result.aporteInicial || 0);
		const aporteMensal = Number(this.result.aporteMensal || 0);

		const benchmarkMap = new Map(
			this.benchmarks.map((benchmark) => [benchmark.nome, benchmark.taxaAnualEsperada])
		);

		const cdiRate = benchmarkMap.get('CDI') ?? 0.12;
		const ibovRate = benchmarkMap.get('Ibovespa') ?? 0.16;

		this.fullChartPoints = serieMensal.map((item, index) => {
			const mes = index + 1;
			const invested = aporteInicial + aporteMensal * mes;
			const portfolio = invested > 0 ? ((item.saldo - invested) / invested) * 100 : 0;
			const cdi = this.compoundReturn(cdiRate, mes) * 100;
			const ibov = this.compoundReturn(ibovRate, mes) * 100;

			return {
				cdi,
				ibov,
				label: this.monthLabel(mes),
				portfolio,
			};
		});
	}

	private updateChart() {
		if (!this.fullChartPoints.length) {
			this.lineChartData = {labels: [], datasets: []};
			return;
		}

		const points = this.fullChartPoints.slice(-this.selectedMonths);

		this.lineChartData = {
			datasets: [
				{
					borderColor: '#f5632b',
					borderWidth: 2.5,
					data: points.map((item) => Number(item.portfolio.toFixed(2))),
					fill: false,
					label: 'Meu Portfólio',
					pointRadius: 0,
					tension: 0.25,
				},
				{
					borderColor: '#5b6670',
					borderWidth: 2.5,
					data: points.map((item) => Number(item.ibov.toFixed(2))),
					fill: false,
					label: 'Ibovespa',
					pointRadius: 0,
					tension: 0.2,
				},
				{
					borderColor: '#1f3c6b',
					borderWidth: 2.5,
					data: points.map((item) => Number(item.cdi.toFixed(2))),
					fill: false,
					label: 'CDI',
					pointRadius: 0,
					tension: 0.15,
				},
			],
			labels: points.map((item) => item.label),
		};
	}

	private compoundReturn(annualRate: number, months: number) {
		return Math.pow(1 + annualRate, months / 12) - 1;
	}

	private monthLabel(monthNumber: number) {
		const now = new Date();
		const date = new Date(now.getFullYear(), now.getMonth() - (this.form.prazoMeses - monthNumber), 1);
		const month = String(date.getMonth() + 1).padStart(2, '0');
		const year = String(date.getFullYear()).slice(-2);

		return `${month}/${year}`;
	}

	toCurrency(value: any) {
		const numericValue = this.toNumber(value);

		return new Intl.NumberFormat('pt-BR', {
			currency: 'BRL',
			style: 'currency',
		}).format(numericValue);
	}

	toPercent(value: any) {
		const numericValue = this.toNumber(value);

		return `${(numericValue * 100).toFixed(2)}%`;
	}

	toDate(value: any) {
		const numericValue = this.toNumber(value);

		if (!numericValue) {
			return '-';
		}

		const epochMillis = numericValue < 1000000000000 ? numericValue * 1000 : numericValue;
		const date = new Date(epochMillis);

		if (Number.isNaN(date.getTime())) {
			return '-';
		}

		return new Intl.DateTimeFormat('pt-BR', {
			dateStyle: 'short',
			timeStyle: 'short',
		}).format(date);
	}

	toPercentNumber(value: any) {
		return `${this.toNumber(value).toFixed(2)}%`;
	}

	private toNumber(value: any) {
		const numericValue = Number(value);

		if (Number.isNaN(numericValue)) {
			return 0;
		}

		return numericValue;
	}
}
