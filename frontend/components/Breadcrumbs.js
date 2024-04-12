import Link from "next/link";

export default function Breadcrumbs(props) {
    return <h2>
        <small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            {props.langId !== undefined && <>
                <Link href={`/language/${props.langId}`}>{props.langName}</Link> {'> '}
            </>}
            {props.steps !== undefined && props.steps.map(s => <>
                <Link href={s.url}>{s.title}</Link> {'> '}
            </>)}
        </small>
        {props.title}
    </h2>
}
