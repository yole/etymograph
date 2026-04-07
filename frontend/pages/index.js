import {allowEdit, cloneGraph, fetchGraphs, syncChanges} from "../api";
import Link from "next/link";
import {useState} from "react";
import {useRouter} from "next/router";
import {Button, Modal, TextInput} from "@mantine/core";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps() {
    return fetchGraphs()
}

export default function Home(props) {
    const graphs = props.loaderData
    const [errorText, setErrorText] = useState("")
    const [cloneModalOpened, setCloneModalOpened] = useState(false)
    const [cloneRepoUrl, setCloneRepoUrl] = useState("")
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

    async function cloneExistingGraph() {
        const trimmedRepoUrl = cloneRepoUrl.trim()
        if (trimmedRepoUrl === "") {
            setErrorText("Repository URL is not specified")
            return
        }

        const response = await cloneGraph(trimmedRepoUrl)
        if (response.status === 200) {
            setErrorText("")
            setCloneRepoUrl("")
            setCloneModalOpened(false)
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
        {allowEdit() && <p>
            <button className="uiButton" onClick={() => setCloneModalOpened(true)}>Clone Data Repository</button>
        </p>}
        <Modal
            opened={cloneModalOpened}
            onClose={() => setCloneModalOpened(false)}
            title="Clone Data Repository"
        >
            <TextInput
                data-autofocus
                label="Git repository URL"
                value={cloneRepoUrl}
                onChange={(event) => setCloneRepoUrl(event.currentTarget.value)}
            />
            <Button style={{marginTop: "1rem"}} onClick={cloneExistingGraph}>Clone</Button>
        </Modal>
    </>
}
