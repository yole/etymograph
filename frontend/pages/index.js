import {allowEdit, fetchGraphs, syncChanges} from "../api";
import Link from "next/link";
import {useState} from "react";
import {useRouter} from "next/router";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps() {
    return fetchGraphs()
}

export default function Home(props) {
    const graphs = props.loaderData
    const [errorText, setErrorText] = useState("")
    const router = useRouter()

    async function syncGraphChanges(graphId) {
        const response = await syncChanges(graphId)
        if (response.status === 200) {
            router.replace(router.asPath)
            return
        }

        const result = await response.json()
        setErrorText(result.message)
    }

    return <>
        <h2>Etymograph</h2>
        <ul>
            {graphs.map(l =>
                <li key={l.id}>
                    <Link href={`/${l.id}`}>{l.name}</Link>{l.status && " (" + l.status + ")"}
                    {allowEdit() && <>
                        {' '}
                        <button className="uiButton" onClick={() => syncGraphChanges(l.id)}>Sync Changes</button>
                    </>}
                </li>
            )}
        </ul>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
