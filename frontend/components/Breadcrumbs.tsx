import Link from "next/link";
import {useContext, useEffect, useRef} from "react";
import {GlobalStateContext, GraphContext} from "@/components/Contexts";
import {allowEdit} from "@/api";
import {useRouter} from "next/router";

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
    const theGraph = globalState !== undefined ? globalState.graphs.find((g) => g.id === graph) : undefined

    const langName = props.langName !== undefined ? props.langName : (
        (globalState !== undefined && props.langId !== undefined)
            ? globalState.languages.find((g) => g.shortName === props.langId)?.name
            : undefined
    )

    useEffect(() => {
        document.title = "Etymograph : " + (props.langName !== undefined ? props.langName + " : " : "") +
            (props.steps !== undefined ? props.steps.map(s => s.title).join(": ") + " : " : "") +
            props.title
    })

    const router = useRouter()
    const inputRef = useRef<HTMLInputElement>(null)
    const currentQ = typeof router.query.q === 'string' ? router.query.q : ''

    function onSubmit(e: React.FormEvent) {
        e.preventDefault()
        const q = inputRef.current?.value?.trim() || ''
        if (!q) return
        const langParam = props.langId ? `&lang=${encodeURIComponent(props.langId)}` : ''
        router.push(`/${graph}/search?q=${encodeURIComponent(q)}${langParam}`)
    }

    return <div style={{marginBottom: '0.75rem'}}><div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
        <h2 style={{marginBottom: '0', marginTop: '0.75rem'}}>
            <small>
                {theGraph === undefined && <><Link href={`/${graph}`}>Etymograph</Link> {'> '}</>}
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
        {allowEdit() && <form onSubmit={onSubmit} style={{marginLeft: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem'}}>
            <input ref={inputRef} type="search" name="q" aria-label="Search words" dir="auto"
                   defaultValue={currentQ} placeholder="Search words…"
                   style={{maxWidth: '24ch'}} />
            <button type="submit" aria-label="Search">🔍</button>
        </form>
        }
    </div></div>
}
