export default {
    template: `
      <div class="post">
        <div class="loading" v-if="loading">
          Loading...
        </div>
    
        <div v-if="error" class="error">
          {{ error }}
        </div>
    
        <div v-if="post" class="content">
          <h2>{{ post.bDelta }}</h2>
          <p>{{ post.oDelta }}</p>
        </div>
      </div>
    `,

    data() {
        return {
            loading: false,
            post: null,
            error: null
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
    methods: {
        fetchData() {
            this.error = this.post = null;
            this.loading = true;
            // replace `getPost` with your data fetching util / API wrapper
            // axios.get("http://104.238.170.152:4031/deltas")
            //     .then(response => {
            //         console.log(response.data);
            //         this.loading = false;
            //         this.post = {};
            //         this.post.bDelta = response.data.length;
            //         // this.post.bDelta = response.data.map(item => item.bDelta);
            //         // this.post.oDelta = response.data.map(item => item.oDelta);
            //         // this.results = response.data.results
            //     }).catch(reason => this.error = reason.toString());
        }

    }
}

function convertDeltaData(raw) {
    return raw.map(item => item.bDelta);

}