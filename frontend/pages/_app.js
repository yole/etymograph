import '@/styles/globals.css'
import {useRouter} from "next/router";
import {GlobalStateContext, GraphContext} from "@/components/Contexts";

export default function App({ Component, pageProps }) {
  const router = useRouter()

  const content = <>
    <Component {...pageProps} />
    <div className="footer">Generated by <a href="https://github.com/yole/etymograph">yole/etymograph</a></div>
  </>

  return <>
    <GraphContext.Provider value={router.query.graph}>
      {pageProps.globalState !== undefined && <GlobalStateContext.Provider value={pageProps.globalState}>
        {content}
      </GlobalStateContext.Provider>}
      {pageProps.globalState === undefined && content}
    </GraphContext.Provider>
  </>
}
