// import VueCharts from 'vue-chartjs';
// import { Bar, Line } from 'vue-chartjs'
// var VueCharts = require('vue-chartjs');
// requirejs(['vue-chartjs.js'], function (Module) {
//     /* use Module */
// });

// var myViewModel = new Vue({el: '#app'});
// var linechart = require('./app/line-chart.js');
import SingleFileComponent from './app/SingleFileComponent.js';
import LineChart from './app/LineChart.js';
import SimpleFetch from './app/SimpleFetch.js'

var viewModel = new Vue({
                            el: '#app',
                            data: {
                                message: 'Simple Chart'
                            },
                            components: {
                                SingleFileComponent,
                                LineChart,
                                SimpleFetch
                            }
                        });

