/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {HttpClient} from '@angular/common/http';
import {Component, Input, OnInit} from '@angular/core';

@Component({
	selector: 'liferay-sample-custom-element-3',
	styleUrls: ['./app.component.css'],
	templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
	@Input('title') title = 'liferay-sample-custom-element-3';

	errorMessage = '';
	etfs: any[] = [];
	health: any;
	healthLoaded = false;
	loading = false;

	form = {
		aporteInicial: 10000,
		aporteMensal: 500,
		prazoMeses: 120,
		taxaAdministracaoAnual: 0.004,
		taxaAnualEsperada: 0.1,
	};

	result: any;

	constructor(private http: HttpClient) {}

	get chartBars() {
		if (!this.result?.serieMensal?.length) {
			return [];
		}

		const series = this.result.serieMensal.filter((_: any, index: number) =>
			index % 6 === 0 || index === this.result.serieMensal.length - 1
		);

		const maxValue = Math.max(...series.map((item: any) => item.saldo), 1);

		return series.map((item: any) => ({
			heightPercent: (item.saldo / maxValue) * 100,
			label: `Mês ${item.mes}: ${this.toCurrency(item.saldo)}`,
		}));
	}

	ngOnInit(): void {
		this.loadHealth();
		this.loadEtfs();
		this.simulate();
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

	toCurrency(value: number) {
		return new Intl.NumberFormat('pt-BR', {
			currency: 'BRL',
			style: 'currency',
		}).format(value || 0);
	}

	toPercent(value: number) {
		return `${((value || 0) * 100).toFixed(2)}%`;
	}
}
