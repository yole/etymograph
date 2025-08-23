import Link from "next/link";
import {allowEdit, fetchAllGraphs, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
  unstable_runtimeJS: true
}

export const getStaticPaths = fetchAllGraphs

export async function getStaticProps(context) {
  return fetchBackend(context.params.graph, `language`, true)
}

export default function Home(props) {
  const languages = props.loaderData
  const router = useRouter()
  const graph = router.query.graph
  const globalState = useContext(GlobalStateContext)
  return <>
      <Breadcrumbs/>
    <ul>
      {languages.map(l => <li key={l.shortName}><Link href={`${graph}/language/${l.shortName}`}>{l.name}</Link></li>)}
    </ul>

    <p><Link href={`${graph}/publications`}>Bibliography</Link></p>
    {allowEdit() && <button onClick={() => router.push(`${graph}/languages/new`)}>Add language</button>}
  </>
}
