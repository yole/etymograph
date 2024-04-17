import Link from "next/link";
import {allowEdit, fetchAllGraphs, fetchBackend} from "@/api";
import {useRouter} from "next/router";

export const getStaticPaths = fetchAllGraphs

export async function getStaticProps(context) {
  return fetchBackend(context.params.graph, `language`)
}

export default function Home(props) {
  const languages = props.loaderData
  const router = useRouter()
  const graph = router.query.graph
  return <>
    <h2>Etymograph</h2>
    <ul>
      {languages.map(l => <li key={l.shortName}><Link href={`${graph}/language/${l.shortName}`}>{l.name}</Link></li>)}
    </ul>

    <p><Link href={`${graph}/publications`}>Bibliography</Link></p>
    {allowEdit() && <Link href={`${graph}/languages/new`}>Add language</Link>}
  </>
}
