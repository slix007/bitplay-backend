export default Vue.component('line-chart', {
    extends: VueChartJs.Line,
    data() {
        return {
            loading: false,
            error: null,
            chartData: {
                labels: [''],
                datasets: []
            }
        }
    },
    created() {
        // fetch the data when the view is created and the data is
        // already being observed
        this.fetchData()
    },
    watch: {
        // call again the method if the route changes
        '$route': 'fetchData'
    },
    mounted() {
    },
    methods: {
        fetchData() {
            this.error = null;
            this.loading = true;
            // replace `getPost` with your data fetching util / API wrapper
            // axios.get("http://104.238.170.152:4031/deltas")
            let hostname = window.location.hostname;
            if (hostname === 'localhost') hostname = '664-vuld.fplay.io';
            axios.get('http://' + hostname + ':4031/deltas?lastHours=2')
                .then(response => {
                    this.loading = false;
                    // let theData = response.data.slice(Math.max(response.data.length - 120, 1));
                    let theData = response.data;
                    console.log(theData);

                    this.chartData.labels = theData.map(item => item.timestamp);
                    let bDeltaArray = theData.map(item => item.bDelta);
                    let oDeltaArray = theData.map(item => item.oDelta);

                    this.chartData.datasets.push({
                                                     label: 'b_delta',
                                                     backgroundColor: '#00FF00',
                                                     data: bDeltaArray
                                                 });
                    this.chartData.datasets.push({
                                                     label: 'o_delta',
                                                     backgroundColor: '#0000FF',
                                                     data: oDeltaArray
                                                 });
                    this.chartData.datasets.push({
                                                     label: 'b_ask',
                                                     backgroundColor: '#00ffb6',
                                                     data: theData.map(item => item.bAsk),
                                                     hidden: true,
                                                 });
                    this.chartData.datasets.push({
                                                     label: 'b_bid',
                                                     backgroundColor: '#ff4500',
                                                     data: theData.map(item => item.bBid),
                                                     hidden: true,
                                                 });
                    this.chartData.datasets.push({
                                                     label: 'o_ask',
                                                     backgroundColor: '#008eff',
                                                     data: theData.map(item => item.oAsk),
                                                     hidden: true,
                                                 });
                    this.chartData.datasets.push({
                                                     label: 'o_bid',
                                                     backgroundColor: '#ff9805',
                                                     data: theData.map(item => item.oBid),
                                                     hidden: true,
                                                 });
                    this.renderChart(this.chartData, {responsive: true, maintainAspectRatio: false});

                }).catch(reason => {
                    this.error = reason.toString();
                    console.log(this.error);

                    this.chartData = {
                        labels: [this.error],
                        datasets: [{}]
                    }
            });
        }
    }
});
