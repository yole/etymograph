import {allowEdit, fetchBackend} from "@/api";
import Link from "next/link";
import {useEffect} from "react";

export async function getStaticProps() {
    return fetchBackend('publications')
}

export default function Publications(props) {
    const publications = props.loaderData

    useEffect(() => { document.title = "Etymograph : Bibliography" })

    return <>
      <h2>
          <small>
              <Link href={`/`}>Etymograph</Link> {'> '}
          </small>
          Bibliography
      </h2>

      {publications.map(p => <>
          <Link href={`/publication/${p.id}`}>{p.refId}</Link>: {p.name}
          <br/>
      </>)}

      <p>{allowEdit() && <Link href="/publications/new">Add publication</Link>}</p>
    </>
}
