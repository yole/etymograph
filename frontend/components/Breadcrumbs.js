import Link from "next/link";
import {useContext, useEffect} from "react";
import {GlobalStateContext, GraphContext} from "@/components/Contexts";

export default function Breadcrumbs(props) {
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

    return <h2>
        <small>
            {theGraph === undefined && <><Link href={`/${graph}`}>Etymograph</Link> {'> '}</>}
            {theGraph !== undefined && <>
                <Link href="/">Etymograph</Link>{' > '}
                <Link href={`/${graph}`}>{theGraph.name}</Link>{' > '}
            </>}
            {props.langId !== undefined && <>
                <Link href={`/${graph}/language/${props.langId}`}>{langName}</Link> {'> '}
            </>}
            {props.steps !== undefined && props.steps.map(s => <>
                <Link href={s.url}>{s.title}</Link> {'> '}
            </>)}
        </small>
        {props.title}
    </h2>
}
