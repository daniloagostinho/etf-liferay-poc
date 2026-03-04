/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {HttpClientTestingModule} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {FormsModule} from '@angular/forms';
import {NgChartsModule} from 'ng2-charts';

import {AppComponent} from './app.component';

describe('AppComponent', () => {
	beforeEach(() =>
		TestBed.configureTestingModule({
			declarations: [AppComponent],
			imports: [FormsModule, HttpClientTestingModule, NgChartsModule],
		})
	);

	it('create the app', () => {
		const fixture = TestBed.createComponent(AppComponent);
		const app = fixture.componentInstance;
		expect(app).toBeTruthy();
	});

	it(`have as title 'liferay-sample-custom-element-3'`, () => {
		const fixture = TestBed.createComponent(AppComponent);
		const app = fixture.componentInstance;
		expect(app.title).toEqual('liferay-sample-custom-element-3');
	});

	it('render simulator title', () => {
		const fixture = TestBed.createComponent(AppComponent);
		fixture.detectChanges();
		const compiled = fixture.nativeElement as HTMLElement;
		expect(compiled.querySelector('h2')?.textContent).toContain(
			'Simulador de ETFs (PoC)'
		);
	});
});
