import Link from "next/link";
import {fetchAllGraphs, fetchBackend, allowEditGraph} from "@/api";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";

export const config = {
  unstable_runtimeJS: true
}

export const getStaticPaths = fetchAllGraphs

export async function getStaticProps(context) {
  return fetchBackend(context.params.graph, `languages`, true)
}

function LanguageList(props) {
  const languages = props.languages
  const graph = props.graph
  return <ul>
    {languages.map(l => <li key={l.shortName}>
      <Link href={`${graph}/language/${l.shortName}`}>{l.name}</Link>
      {l.descendantLanguages && <LanguageList languages={l.descendantLanguages} graph={graph} />}
    </li>)}
  </ul>
}

export default function Home(props) {
  const languages = props.loaderData
  const router = useRouter()
  const graph = router.query.graph
  const canEdit = allowEditGraph()
  return <>
      <Breadcrumbs title="Languages"/>
      <LanguageList languages={languages} graph={graph} />

    <p><Link href={`${graph}/publications`}>Bibliography</Link></p>
    {canEdit && <button onClick={() => router.push(`${graph}/languages/new`)}>Add language</button>}
  </>
}
