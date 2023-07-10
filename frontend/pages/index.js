import Link from "next/link";
import {fetchBackend} from "@/api";

export async function getStaticProps() {
  return fetchBackend('language')
}

export default function Home(props) {
  const languages = props.loaderData
  return <>
    <h2>Etymograph</h2>
    <ul>
      {languages.map(l => <li key={l.shortName}><Link href={`language/${l.shortName}`}>{l.name}</Link></li>)}
    </ul>
    <Link href='languages/new'>Add language</Link>
  </>
}
