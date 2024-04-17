import Link from "next/link";
import {useEffect} from "react";
import {useRouter} from "next/router";

export default function Breadcrumbs(props) {
    const router = useRouter()
    const graph = router.query.graph

    useEffect(() => {
        document.title = "Etymograph : " + (props.langName !== undefined ? props.langName + " : " : "") +
            (props.steps !== undefined ? props.steps.map(s => s.title).join(": ") + " : " : "") +
            props.title
    }, []);

    return <h2>
        <small>
            <Link href={`/${graph}`}>Etymograph</Link> {'> '}
            {props.langId !== undefined && <>
                <Link href={`/${graph}/language/${props.langId}`}>{props.langName}</Link> {'> '}
            </>}
            {props.steps !== undefined && props.steps.map(s => <>
                <Link href={s.url}>{s.title}</Link> {'> '}
            </>)}
        </small>
        {props.title}
    </h2>
}
