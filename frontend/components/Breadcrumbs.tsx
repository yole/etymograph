import Link from "next/link";
import {useContext, useEffect, useRef} from "react";
import {AuthContext, GlobalStateContext, GraphContext} from "@/components/Contexts";
import {hasBackend, backendUrl, logout} from "@/api";
import {useRouter} from "next/router";
import {ActionIcon, Avatar, Menu} from "@mantine/core";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faMagnifyingGlass} from "@fortawesome/free-solid-svg-icons";

interface BreadcrumbStep {
    title: string;
    url: string;
}

interface BreadcrumbsProps {
    langName?: string;
    langId?: string;
    steps?: BreadcrumbStep[];
    title?: any;
    children?: React.ReactNode;
}

export default function Breadcrumbs(props: BreadcrumbsProps) {
    const graph = useContext(GraphContext)
    const globalState = useContext(GlobalStateContext)
    const auth = useContext(AuthContext)
    const theGraph = globalState !== undefined ? globalState.graphs.find((g) => g.id === graph) : undefined

    const langName = props.langName !== undefined ? props.langName : (
        (globalState !== undefined && props.langId !== undefined)
            ? globalState.languages.find((g) => g.shortName === props.langId)?.name
            : undefined
    )

    useEffect(() => {
        document.title = "Etymograph" + (graph !== undefined ? " : " : "") + (props.langName !== undefined ? props.langName + " : " : "") +
            (props.steps !== undefined ? props.steps.map(s => s.title).join(": ") + " : " : "") +
            (props.title ?? "")
    })

    const router = useRouter()
    const inputRef = useRef<HTMLInputElement>(null)
    const currentQ = typeof router.query.q === 'string' ? router.query.q : ''
    const authStatus = auth?.authStatus
    const displayUser = authStatus?.name || authStatus?.email

    function onSubmit(e: React.FormEvent) {
        e.preventDefault()
        const q = inputRef.current?.value?.trim() || ''
        if (!q) return
        const langParam = props.langId ? `&lang=${encodeURIComponent(props.langId)}` : ''
        router.push(`/${graph}/search?q=${encodeURIComponent(q)}${langParam}`)
    }

    async function onLogout() {
        await logout()
        router.reload()
    }

    const controls = <div style={{marginLeft: '1rem', display: 'flex', alignItems: 'center', gap: '0.75rem'}}>
        {authStatus?.authEnabled === true && <>
            {authStatus.authenticated
                ? <Menu shadow="md" width={160} position="bottom-end">
                    <Menu.Target>
                        <Avatar
                            src={authStatus.pictureUrl}
                            alt={displayUser || "User"}
                            radius="xl"
                            size="sm"
                            style={{cursor: 'pointer'}}
                        />
                    </Menu.Target>
                    <Menu.Dropdown>
                        <Menu.Item onClick={onLogout}>Log out</Menu.Item>
                    </Menu.Dropdown>
                </Menu>
                : <a href={backendUrl("oauth2/authorization/google")}>Log in</a>}
        </>}
        {hasBackend() && graph && <form onSubmit={onSubmit} style={{display: 'flex', alignItems: 'center', gap: '0.5rem'}}>
            <input ref={inputRef} type="text" name="q" aria-label="Search words" dir="auto"
                   defaultValue={currentQ} placeholder="Search words…"
                   style={{maxWidth: '24ch'}} />
            <ActionIcon type="submit" aria-label="Search" variant="default">
                <FontAwesomeIcon icon={faMagnifyingGlass}/>
            </ActionIcon>
        </form>}
    </div>

    return <div style={{marginBottom: '0.75rem'}}><div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
        <h2 style={{marginBottom: '0', marginTop: '0.75rem'}}>
            <small>
                {theGraph === undefined && "Etymograph"}
                {theGraph !== undefined && <>
                    <Link href="/">Etymograph</Link>{' > '}
                    <Link href={`/${graph}`}>{theGraph.name}</Link>
                </>}
                {props.langId !== undefined && <>
                    {' > '}<Link href={`/${graph}/language/${props.langId}`}>{langName}</Link>
                </>}
                {props.steps !== undefined && props.steps.map(s => <>
                    {' > '}<Link href={s.url}>{s.title}</Link>
                </>)}
            </small>
            {props.title && ' > ' + props.title}
            {props.children && <>{' > '}{props.children}</>}
        </h2>
        {controls}
    </div></div>
}
