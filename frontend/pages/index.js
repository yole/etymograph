import {hasBackend, cloneGraph, fetchGraphs, syncChanges, revertChanges, callApiAndRefresh} from "../api";
import Link from "next/link";
import {useContext, useState} from "react";
import {useRouter} from "next/router";
import {Button, Modal, TextInput} from "@mantine/core";
import Breadcrumbs from "../components/Breadcrumbs";
import {AuthContext} from "../components/Contexts";

export async function getStaticProps() {
    return fetchGraphs()
}

export default function Home(props) {
    const graphs = props.loaderData
    const [errorText, setErrorText] = useState("")
    const [cloneModalOpened, setCloneModalOpened] = useState(false)
    const [cloneRepoUrl, setCloneRepoUrl] = useState("")
    const router = useRouter()
    const auth = useContext(AuthContext)

    async function syncGraphChanges(graphId) {
        callApiAndRefresh(() => syncChanges(graphId), router, setErrorText)
    }

    async function revertGraphChanges(graphId) {
        callApiAndRefresh(() => revertChanges(graphId), router, setErrorText)
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
        <Breadcrumbs/>
        <ul>
            {graphs.map(l =>
                <li key={l.id}>
                    <Link href={`/${l.id}`}>{l.name}</Link>{hasBackend() && l.status && " (" + l.status + ")"}
                    {auth?.authStatus?.editableGraphs?.includes(l.id) && <>
                        {' '}
                        <button className="uiButton" onClick={() => syncGraphChanges(l.id)}>Sync Changes</button>
                        {' '}
                        <button className="uiButton" onClick={() => revertGraphChanges(l.id)}>Revert Changes</button>
                    </>}
                </li>
            )}
        </ul>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
        {hasBackend() && (auth?.authStatus?.authEnabled === false || auth?.authStatus?.authenticated === true) && <p>
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
